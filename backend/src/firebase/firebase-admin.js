import fs from 'node:fs';
import { initializeApp, applicationDefault, getApps, deleteApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getDatabase } from 'firebase-admin/database';
import { config } from '../config.js';

let cached = null;
let initialized = false;
let initError = null;

// Inicializa (uma única vez) o SDK Admin com a conta de serviço.
// Retorna null se a sincronização estiver desativada ou sem credenciais.
export function getFirebase() {
  if (cached !== null) return cached;

  if (!config.firebaseSyncEnabled) {
    initError = null;
    cached = null;
    return null;
  }

  if (!initialized) {
    initialized = true;
    try {
      let credential;
      if (config.firebaseServiceAccount && fs.existsSync(config.firebaseServiceAccount)) {
        credential = JSON.parse(fs.readFileSync(config.firebaseServiceAccount, 'utf8'));
      } else {
        // Fallback: credenciais padrão do ambiente (GOOGLE_APPLICATION_CREDENTIALS)
        credential = applicationDefault();
      }

      const appOptions = { credential };
      if (config.firebaseDatabaseUrl) appOptions.databaseURL = config.firebaseDatabaseUrl;

      const existing = getApps()[0];
      if (existing) deleteApp(existing);
      const app = initializeApp(appOptions);

      // O app Android usa FirebaseFirestore.getInstance("securitas"):
      // uma instância nomeada do Firestore. firebase-admin suporta
      // databaseId customizado no getFirestore(app, { databaseId }).
      let firestore;
      try {
        firestore = getFirestore(app, { databaseId: config.firestoreId });
      } catch {
        firestore = getFirestore(app, { databaseId: '(default)' });
      }

      const rtdb = config.firebaseDatabaseUrl ? getDatabase(app) : null;

      cached = { app, firestore, rtdb };
      console.log(
        `[firebase] inicializado (Firestore db="${config.firestoreId}", RTDB=${rtdb ? 'sim' : 'não'})`
      );
    } catch (e) {
      initError = e;
      cached = null;
      console.warn(
        `[firebase] sincronização habilitada mas falhou ao inicializar: ${e.message}`
      );
      console.warn(
        '[firebase] verifique FIREBASE_SERVICE_ACCOUNT e FIREBASE_DATABASE_URL no .env'
      );
    }
  }

  return cached;
}

export function getFirebaseInitError() {
  return initError;
}