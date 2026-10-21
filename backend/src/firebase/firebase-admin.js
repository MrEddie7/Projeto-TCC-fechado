import fs from 'node:fs';
import { initializeApp, applicationDefault, cert, getApps } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { getDatabase } from 'firebase-admin/database';
import { config } from '../config.js';

let cached = null;
let initialized = false;
let initError = null;

// Inicializa (uma única vez) o SDK Admin. Firestore é o banco principal da API.
export function getFirebase() {
  if (cached !== null) return cached;

  if (!initialized) {
    initialized = true;
    try {
      if (config.firebaseServiceAccount && !fs.existsSync(config.firebaseServiceAccount)) {
        // Caminho do service account configurado no .env, mas arquivo ausente.
        // Falhar de forma clara (em vez de cair no fallback ADC, que trava em
        // máquinas de desenvolvimento tentando obter OAuth token em loop).
        throw new Error(
          `Service account não encontrado: ${config.firebaseServiceAccount} ` +
            '(baixe a chave privada no Firebase Console > Configurações > Contas ' +
            'de serviço e salve neste caminho)'
        );
      }

      let credential;
      if (config.firebaseServiceAccount && fs.existsSync(config.firebaseServiceAccount)) {
        // firebase-admin v13+ exige um objeto Credential (cert()), não o JSON cru.
        credential = cert(JSON.parse(fs.readFileSync(config.firebaseServiceAccount, 'utf8')));
      } else {
        // Fallback: credenciais padrão do ambiente (GOOGLE_APPLICATION_CREDENTIALS)
        credential = applicationDefault();
      }

      const appOptions = { credential };
      if (config.firebaseDatabaseUrl) appOptions.databaseURL = config.firebaseDatabaseUrl;

      const app = getApps()[0] || initializeApp(appOptions);

// O app Android usa FirebaseFirestore.getInstance("securitas"):
// uma instância nomeada do Firestore. O SDK Admin recebe o
// databaseId como 2º argumento de getFirestore(app, databaseId).
      let firestore;
      try {
        firestore = getFirestore(app, config.firestoreId);
      } catch {
        firestore = getFirestore(app, '(default)');
      }

      if (!config.firebaseDatabaseUrl) {
        throw new Error('FIREBASE_DATABASE_URL é obrigatório para telemetria e localização móvel');
      }
      const rtdb = getDatabase(app);
      const auth = getAuth(app);

      cached = { app, firestore, rtdb, auth };
      // stderr: é diagnóstico. O stdout é reservado para dados de programa
      // (ex.: o TSV do list-devices.js consumido pelo api.sh).
      console.error(
        `[firebase] inicializado (Firestore db="${config.firestoreId}", RTDB=sim)`
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
