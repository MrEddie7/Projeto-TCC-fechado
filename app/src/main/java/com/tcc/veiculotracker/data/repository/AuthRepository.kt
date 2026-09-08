package com.tcc.veiculotracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.tcc.veiculotracker.data.local.dao.UserDao
import com.tcc.veiculotracker.data.local.entity.User
import com.tcc.veiculotracker.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val userDao: UserDao,
    private val syncManager: SyncManager
) {

    private val firebaseAuth = FirebaseAuth.getInstance()

    // ── Google Sign-In ───────────────────────────────────────────────────

    /**
     * Autentica com o Firebase usando o ID token do Google (Credential Manager)
     * e garante que o usuário exista no banco local (Room), vinculado ao UID.
     */
    suspend fun signInWithGoogle(idToken: String): User {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user ?: throw IllegalStateException("Usuário do Firebase não encontrado")
        return upsertLocalUser(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name = firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: "Usuário",
            phone = firebaseUser.phoneNumber ?: ""
        )
    }

    // ── Email/Senha (provedor Email/Password do Firebase) ───────────────

    /**
     * Cria uma conta real no Firebase Authentication e vincula ao usuário local.
     */
    suspend fun registerWithEmail(
        name: String,
        email: String,
        password: String,
        phone: String
    ): User {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = authResult.user ?: throw IllegalStateException("Usuário do Firebase não encontrado")
        firebaseUser.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name).build()
        ).await()
        return upsertLocalUser(
            uid = firebaseUser.uid,
            email = email,
            name = name,
            phone = phone
        )
    }

    /**
     * Login por email/senha via Firebase. Retorna null se as credenciais forem inválidas.
     */
    suspend fun signInWithEmail(email: String, password: String): User? {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: return null
            upsertLocalUser(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: email,
                name = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@"),
                phone = firebaseUser.phoneNumber ?: ""
            )
        } catch (e: FirebaseAuthInvalidUserException) {
            null
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            null
        }
    }

    // ── Sessão / perfil ─────────────────────────────────────────────────

    suspend fun signOut() {
        firebaseAuth.signOut()
    }

    val currentFirebaseUid: String?
        get() = firebaseAuth.currentUser?.uid

    fun getUserById(userId: Long): Flow<User?> {
        return userDao.getUserById(userId)
    }

    suspend fun updateUser(user: User) {
        userDao.update(user)
        syncManager.syncUser(user)
    }

    // ── Suporte interno ─────────────────────────────────────────────────

    private suspend fun upsertLocalUser(
        uid: String,
        email: String,
        name: String,
        phone: String
    ): User {
        var user = userDao.getUserByFirebaseUid(uid)
        if (user == null) {
            val id = userDao.insert(
                User(name = name, email = email, phone = phone, firebaseUid = uid)
            )
            user = userDao.getUserByFirebaseUid(uid)
                ?: User(id = id, name = name, email = email, phone = phone, firebaseUid = uid)
        }
        // Compartilha o ID local com a nuvem (mesma chave de documento no Firestore).
        syncManager.syncUser(user)
        return user
    }
}