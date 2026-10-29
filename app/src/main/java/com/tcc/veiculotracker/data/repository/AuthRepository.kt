package com.tcc.veiculotracker.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.tcc.veiculotracker.data.model.UserModel
import com.tcc.veiculotracker.data.remote.ApiClient
import com.tcc.veiculotracker.data.sync.SyncManager
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.tasks.await

/**
 * Gerencia autenticação via Firebase Auth (email/senha e Google).
 * O Room foi removido — o perfil do usuário vive no Firestore e os campos
 * essenciais (id, nome, email) são mantidos em SharedPreferences para acesso
 * imediato sem I/O.
 *
 * A "identidade local" é derivada diretamente do FirebaseUser retornado após
 * o login, sem banco local intermediário.
 */
class AuthRepository(
    private val context: Context,
    private val syncManager: SyncManager
) {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    // ── Google Sign-In ────────────────────────────────────────────────────────

    suspend fun signInWithGoogle(idToken: String): UserModel {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user
            ?: throw IllegalStateException("Usuário do Firebase não encontrado")
        return buildAndPersistUser(
            uid   = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name  = firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: "Usuário",
            phone = firebaseUser.phoneNumber ?: ""
        )
    }

    // ── Email/Senha ───────────────────────────────────────────────────────────

    suspend fun registerWithEmail(
        name: String,
        email: String,
        password: String,
        phone: String
    ): UserModel {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = authResult.user
            ?: throw IllegalStateException("Usuário do Firebase não encontrado")
        firebaseUser.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name).build()
        ).await()
        return buildAndPersistUser(
            uid   = firebaseUser.uid,
            email = email,
            name  = name,
            phone = phone
        )
    }

    suspend fun signInWithEmail(email: String, password: String): UserModel? {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: return null
            buildAndPersistUser(
                uid   = firebaseUser.uid,
                email = firebaseUser.email ?: email,
                name  = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                            ?: email.substringBefore("@"),
                phone = firebaseUser.phoneNumber ?: ""
            )
        } catch (e: FirebaseAuthInvalidUserException)        { null }
          catch (e: FirebaseAuthInvalidCredentialsException) { null }
    }

    // ── Sessão / perfil ───────────────────────────────────────────────────────

    suspend fun signOut() {
        firebaseAuth.signOut()
    }

    val currentFirebaseUid: String?
        get() = firebaseAuth.currentUser?.uid

    /** Retorna o UserModel do usuário atualmente logado a partir das prefs. */
    fun currentUser(): UserModel? {
        val uid = prefs.getString(Constants.KEY_FIREBASE_UID, null) ?: return null
        return UserModel(
            id          = prefs.getLong(Constants.KEY_USER_ID, 0),
            firebaseUid = uid,
            name        = prefs.getString(Constants.KEY_USER_NAME, "") ?: "",
            email       = prefs.getString(Constants.KEY_USER_EMAIL, "") ?: "",
            phone       = ""
        )
    }

    suspend fun updateUser(user: UserModel) {
        saveUserPrefs(user)
        syncManager.syncUser(user)
    }

    // ── Suporte interno ───────────────────────────────────────────────────────

    private suspend fun buildAndPersistUser(
        uid: String, email: String, name: String, phone: String
    ): UserModel {
        // Usa o hashCode do UID como ID numérico estável (evita Room autoincrement).
        val id = uid.hashCode().toLong().let { if (it < 0) -it else it }
        val user = UserModel(
            id          = id,
            firebaseUid = uid,
            email       = email,
            name        = name,
            phone       = phone
        )
        saveUserPrefs(user)
        // Grava no Firestore e envia os veículos da conta.
        syncManager.syncAccountToCloud(user)
        // Registra este aparelho na API: é o que faz o celular aparecer como
        // "dispositivo registrado" no painel administrativo.
        ensureDeviceRegistered()
        // Envia à API o token de push deste aparelho (alertas de bloqueio).
        com.tcc.veiculotracker.data.remote.PushTokenManager.registerCurrentToken(context)
        return user
    }

    /**
     * Registra/atualiza este aparelho na API, usando o ID Token do Firebase
     * (a API valida o token no header Authorization). Nunca lança: sem rede ou
     * sem API configurada, o login continua funcionando normalmente.
     */
    suspend fun ensureDeviceRegistered(): Result<ApiClient.MobileDeviceDto>? =
        runCatching { syncManager.backendRepository?.registerCurrentDevice() }.getOrNull()

    private fun saveUserPrefs(user: UserModel) {
        prefs.edit().apply {
            putLong(Constants.KEY_USER_ID, user.id)
            putString(Constants.KEY_FIREBASE_UID, user.firebaseUid)
            putString(Constants.KEY_USER_NAME, user.name)
            putString(Constants.KEY_USER_EMAIL, user.email)
            apply()
        }
    }
}
