package com.chanmin.nabiledger.cloud

import android.content.Context
import android.content.SharedPreferences
import com.chanmin.nabiledger.data.Entry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Profile(val uid: String, val nickname: String, val code: String)
data class Friend(val uid: String, val nickname: String)

/**
 * Firestore 구조
 *   codes/{친구코드}                 { uid }
 *   users/{uid}                      { nickname, code }
 *   users/{uid}/friends/{친구uid}    { since }   ← 여기 있는 사람이 내 내역을 볼 수 있음
 *   users/{uid}/entries/{entryId}    숨기지 않은 기록만 (숨긴 기록은 절대 업로드하지 않음)
 * 접근 제어는 firestore.rules 참고.
 */
object Cloud {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private var appCtx: Context? = null
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (appCtx != null) return
        val app = context.applicationContext
        appCtx = app
        prefs = app.getSharedPreferences("nabi_cloud", Context.MODE_PRIVATE)
        val uid = prefs.getString("uid", null)
        val name = prefs.getString("nickname", null)
        val code = prefs.getString("code", null)
        if (uid != null && name != null && code != null) _profile.value = Profile(uid, name, code)
    }

    /** google-services.json 없이 빌드됐으면 false — 친구 기능만 꺼지고 나머지는 그대로 동작 */
    fun available(): Boolean = appCtx?.let { FirebaseApp.getApps(it).isNotEmpty() } ?: false

    private fun ready(): Boolean = available() && _profile.value != null

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private fun users() = db.collection("users")
    private fun entriesOf(uid: String) = users().document(uid).collection("entries")
    private fun friendsOf(uid: String) = users().document(uid).collection("friends")

    private suspend fun uid(): String {
        auth.currentUser?.let { return it.uid }
        val user = auth.signInAnonymously().await().user
            ?: throw IllegalStateException("로그인에 실패했다냥")
        return user.uid
    }

    // ---------- 프로필 ----------

    /** 처음 시작하거나 닉네임을 바꿀 때. 친구 코드는 한 번 만들면 유지 */
    suspend fun saveProfile(nickname: String): Profile {
        val uid = uid()
        val name = nickname.trim().take(12).ifEmpty { "나비 집사" }
        var code = users().document(uid).get().await().getString("code")
        if (code == null) {
            for (attempt in 0 until 6) {
                val candidate = (1..8).map { ALPHABET.random() }.joinToString("")
                val ok = try {
                    db.collection("codes").document(candidate).set(mapOf("uid" to uid)).await()
                    true
                } catch (e: Exception) {
                    false // 이미 다른 사람이 쓰는 코드 → 규칙에서 거부됨
                }
                if (ok) {
                    code = candidate
                    break
                }
            }
        }
        val finalCode = code ?: throw IllegalStateException("친구 코드를 만들지 못했다냥. 다시 시도해줘냥")
        users().document(uid).set(mapOf("nickname" to name, "code" to finalCode)).await()
        prefs.edit().putString("uid", uid).putString("nickname", name).putString("code", finalCode).apply()
        val p = Profile(uid, name, finalCode)
        _profile.value = p
        return p
    }

    // ---------- 내 기록 동기화 ----------

    private fun Entry.toMap(): Map<String, Any> = mapOf(
        "day" to epochDay,
        "amount" to amount,
        "category" to category,
        "memo" to memo,
        "income" to isIncome,
        "createdAt" to createdAt,
    )

    private fun DocumentSnapshot.toEntry(): Entry? {
        val id = id.toLongOrNull() ?: return null
        return Entry(
            id = id,
            epochDay = getLong("day") ?: return null,
            amount = getLong("amount") ?: return null,
            category = getString("category") ?: "기타",
            memo = getString("memo") ?: "",
            isIncome = getBoolean("income") ?: false,
            createdAt = getLong("createdAt") ?: 0L,
        )
    }

    /** 기록 추가·수정 후 호출. 숨긴 기록이면 서버에서 지운다 */
    fun push(e: Entry) {
        if (!ready()) return
        scope.launch {
            runCatching {
                val doc = entriesOf(uid()).document(e.id.toString())
                if (e.hidden) doc.delete().await() else doc.set(e.toMap()).await()
            }
        }
    }

    fun remove(id: Long) {
        if (!ready()) return
        scope.launch {
            runCatching { entriesOf(uid()).document(id.toString()).delete().await() }
        }
    }

    /** 앱을 켤 때·프로필을 만들 때: 서버를 이 기기의 '숨기지 않은 기록'과 똑같이 맞춘다 */
    fun syncAll(entries: List<Entry>) {
        if (!ready()) return
        scope.launch {
            runCatching {
                val col = entriesOf(uid())
                val remote = col.get().await().documents.map { it.id }.toSet()
                val visible = entries.filter { !it.hidden }
                val visibleIds = visible.map { it.id.toString() }.toSet()
                visible.filter { it.id.toString() !in remote }.chunked(400).forEach { chunk ->
                    val b = db.batch()
                    chunk.forEach { b.set(col.document(it.id.toString()), it.toMap()) }
                    b.commit().await()
                }
                (remote - visibleIds).chunked(400).forEach { chunk ->
                    val b = db.batch()
                    chunk.forEach { b.delete(col.document(it)) }
                    b.commit().await()
                }
            }
        }
    }

    // ---------- 친구 ----------

    /** 친구 코드를 입력하면 서로 친구가 된다 (양쪽 friends에 서로를 추가) */
    suspend fun addFriend(rawCode: String): Friend {
        val me = uid()
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) throw IllegalArgumentException("코드를 입력해줘냥")
        val fuid = db.collection("codes").document(code).get().await().getString("uid")
            ?: throw IllegalArgumentException("그런 코드는 없다냥")
        if (fuid == me) throw IllegalArgumentException("그건 내 코드다냥!")
        val since = mapOf("since" to System.currentTimeMillis())
        friendsOf(me).document(fuid).set(since).await()
        friendsOf(fuid).document(me).set(since).await()
        val name = users().document(fuid).get().await().getString("nickname") ?: "친구"
        return Friend(fuid, name)
    }

    suspend fun friends(): List<Friend> {
        val me = uid()
        val ids = friendsOf(me).get().await().documents.map { it.id }
        return ids.map { id ->
            val name = runCatching { users().document(id).get().await().getString("nickname") }
                .getOrNull() ?: "친구"
            Friend(id, name)
        }
    }

    suspend fun friendEntries(friendUid: String, fromDay: Long): List<Entry> =
        entriesOf(friendUid)
            .whereGreaterThanOrEqualTo("day", fromDay)
            .get().await()
            .documents.mapNotNull { it.toEntry() }

    suspend fun removeFriend(friendUid: String) {
        val me = uid()
        friendsOf(me).document(friendUid).delete().await()
        runCatching { friendsOf(friendUid).document(me).delete().await() }
    }
}
