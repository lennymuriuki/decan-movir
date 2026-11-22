const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_decan_123";
const BOB_UID = "bob_decan_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProfilePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    displayName: "Alice Cinema",
    email: "alice@example.com",
    preferredGenre: "Sci-Fi & Fantasy",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validMoviePayload(uid, movieId = "itunes_17283940", overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    movieId,
    title: "Dune: Part Two",
    director: "Denis Villeneuve",
    genre: "Sci-Fi & Fantasy",
    releaseYear: "2024",
    posterUrl: "https://example.com/dune2.jpg",
    previewUrl: "https://example.com/dune2_trailer.m4v",
    synopsis: "Paul Atreides unites with Chani and the Fremen.",
    runtimeMinutes: 166,
    contentRating: "PG-13",
    watchStatus: "WANT_TO_WATCH",
    favorite: false,
    personalRating: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("itunes_100")
      .set(validMoviePayload(ALICE_UID, "itunes_100"))
  );
});

test("User profile: owner create, partial update, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Valid create
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validProfilePayload(ALICE_UID))
  );

  // Partial update of preferredGenre
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredGenre: "Drama",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject cross-user read or write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject root users list query
  await assertFails(aliceDb.collection("users").get());
});

test("Library entries: Kotlin-mirrored create, update, list, delete, and negative bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const movieId = "itunes_17283940";
  const created = new Date(Date.now() - 10000);

  // Valid create (no @DocumentId `id` field, nullable optional fields omitted)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc(movieId)
      .set(validMoviePayload(ALICE_UID, movieId, { createdAt: created, updatedAt: created }))
  );

  // Valid list query on user-scoped subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("library").get()
  );

  // Valid partial update (watchStatus, favorite, personalRating, personalNote, updatedAt)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc(movieId)
      .update({
        watchStatus: "WATCHED",
        favorite: true,
        personalRating: 5,
        personalNote: "Monumental sound design and cinematography.",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc(movieId)
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject invalid rating > 5
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc(movieId)
      .update({
        personalRating: 6,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject invalid watchStatus enum
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc(movieId)
      .update({
        watchStatus: "INVALID_STATUS",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject `@DocumentId` `id` leaked into document payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("itunes_999")
      .set({
        id: "itunes_999",
        ...validMoviePayload(ALICE_UID, "itunes_999"),
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc("itunes_future")
      .set(
        validMoviePayload(ALICE_UID, "itunes_future", {
          createdAt: new Date(Date.now() + 60000),
          updatedAt: new Date(Date.now() + 60000),
        })
      )
  );

  // Reject cross-user read, list, update, delete
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc(movieId)
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("library").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc(movieId)
      .delete()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("library")
      .doc(movieId)
      .delete()
  );
});
