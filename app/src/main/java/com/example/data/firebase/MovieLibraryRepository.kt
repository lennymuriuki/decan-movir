package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.UserProfile
import com.example.data.model.WatchStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MovieLibraryRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeLibrary(userId: String): Flow<List<SavedMovieEntry>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Authenticated user mismatch." }
    val path = "users/$uid/library"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("library")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            SavedMovieEntry::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Authenticated user mismatch." }
    val path = "users/$uid"
    emitAll(
      db.collection("users")
        .document(uid)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              UserProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
    val path = "users/${user.uid}"
    return try {
      val uid = requireUserId()
      check(uid == user.uid) { "Signed-in user changed; refusing profile write." }
      val docRef = db.collection("users").document(uid)
      val existing = docRef.get().await()
      val displayName =
        (user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Cinema Viewer").take(80)
      val email = user.email?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
      val photoUrl = user.photoUrl?.toString()?.take(500)?.takeIf { it.isNotEmpty() }

      if (!existing.exists()) {
        val createPayload =
          mapOf(
            "userId" to uid,
            "displayName" to displayName,
            "email" to email,
            "photoUrl" to photoUrl,
            "preferredGenre" to "All",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.set(createPayload).await()
      } else {
        val updatePayload =
          mapOf(
            "displayName" to displayName,
            "email" to email,
            "photoUrl" to photoUrl,
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun updatePreferredGenre(genre: String): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
    return try {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid)
      path = docRef.path
      docRef
        .update(
          mapOf(
            "preferredGenre" to genre.trim().ifBlank { "All" }.take(40),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun saveOrUpdateLibraryEntry(
    movie: Movie,
    watchStatus: WatchStatus,
    favorite: Boolean,
    personalRating: Int,
    personalNote: String?,
  ): Result<Unit> {
    val safeMovieId = Movie.sanitizeId(movie.id)
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/library/$safeMovieId"
    return try {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid).collection("library").document(safeMovieId)
      path = docRef.path

      val safeTitle = movie.title.trim().ifBlank { "Untitled Film" }.take(200)
      val safeDirector = movie.director.trim().take(120)
      val safeGenre = movie.genre.trim().ifBlank { "Cinema" }.take(60)
      val safeYear = movie.releaseYear.trim().take(10)
      val safePoster = movie.posterUrl.trim().take(500)
      val safePreview = movie.previewUrl?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
      val safeSynopsis = movie.synopsis.trim().take(2000).takeIf { it.isNotEmpty() }
      val safeRuntime = movie.runtimeMinutes.coerceIn(0, 1000)
      val safeContentRating = movie.contentRating.trim().take(20).takeIf { it.isNotEmpty() }
      val safeRating = personalRating.coerceIn(0, 5)
      val safeNote = personalNote?.trim()?.take(1000)

      val snapshot = docRef.get().await()
      if (!snapshot.exists()) {
        val createPayload =
          mapOf(
            "userId" to uid,
            "movieId" to safeMovieId,
            "title" to safeTitle,
            "director" to safeDirector,
            "genre" to safeGenre,
            "releaseYear" to safeYear,
            "posterUrl" to safePoster,
            "previewUrl" to safePreview,
            "synopsis" to safeSynopsis,
            "runtimeMinutes" to safeRuntime,
            "contentRating" to safeContentRating,
            "watchStatus" to watchStatus.name,
            "favorite" to favorite,
            "personalRating" to safeRating,
            "personalNote" to safeNote?.takeIf { it.isNotEmpty() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.set(createPayload).await()
      } else {
        val updatePayload =
          mapOf(
            "title" to safeTitle,
            "director" to safeDirector,
            "genre" to safeGenre,
            "releaseYear" to safeYear,
            "posterUrl" to safePoster,
            "previewUrl" to safePreview,
            "synopsis" to safeSynopsis,
            "runtimeMinutes" to safeRuntime,
            "contentRating" to safeContentRating,
            "watchStatus" to watchStatus.name,
            "favorite" to favorite,
            "personalRating" to safeRating,
            "personalNote" to (safeNote ?: ""),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun removeFromLibrary(movieId: String): Result<Unit> {
    val safeMovieId = Movie.sanitizeId(movieId)
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/library/$safeMovieId"
    return try {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid).collection("library").document(safeMovieId)
      path = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }
}
