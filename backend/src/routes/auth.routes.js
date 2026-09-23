import { Router } from 'express';
import {
  createUser,
  createToken,
  deleteToken,
  getUserByEmail,
  verifyPassword,
  publicUser,
} from '../services/auth.service.js';
import { asyncHandler } from '../middleware/error.js';
import { requireAuth } from '../middleware/auth.js';

const router = Router();

// Registro de nova conta
router.post(
  '/register',
  asyncHandler(async (req, res) => {
    const { name, email, phone, password } = req.body || {};
    if (!name || !email || !password) {
      res.status(400).json({ error: 'Informe name, email e password' });
      return;
    }
    if (String(email).length < 5 || !String(email).includes('@')) {
      res.status(400).json({ error: 'E-mail inválido' });
      return;
    }
    if (String(password).length < 6) {
      res.status(400).json({ error: 'A senha deve ter no mínimo 6 caracteres' });
      return;
    }
    if (getUserByEmail(email)) {
      res.status(409).json({ error: 'E-mail já cadastrado' });
      return;
    }
    const user = createUser({ name, email, phone, password });
    const { token, expiresAt } = createToken(user.id);
    res.status(201).json({ user: publicUser(user), token, expiresAt });
  })
);

// Login
router.post(
  '/login',
  asyncHandler(async (req, res) => {
    const { email, password } = req.body || {};
    if (!email || !password) {
      res.status(400).json({ error: 'Informe email e password' });
      return;
    }
    const user = getUserByEmail(email);
    if (!user || !verifyPassword(password, user.password_hash)) {
      res.status(401).json({ error: 'Credenciais inválidas' });
      return;
    }
    const { token, expiresAt } = createToken(user.id);
    res.json({ user: publicUser(user), token, expiresAt });
  })
);

// Perfil do usuário logado
router.get(
  '/me',
  requireAuth,
  asyncHandler(async (req, res) => {
    res.json({ user: publicUser(req.user) });
  })
);

// Logout (invalida o token)
router.post(
  '/logout',
  requireAuth,
  asyncHandler(async (req, res) => {
    const token = req.headers.authorization.slice(7);
    deleteToken(token);
    res.json({ ok: true });
  })
);

export default router;