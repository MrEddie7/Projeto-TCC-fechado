<p align="center">
  <strong style="font-size: 1.6rem">VeiculoTracker · Securitas</strong><br>
  Site institucional + painel do grupo — TCC Laravel Breeze
</p>

---

Este é o **segundo aplicativo** do TCC: um site em **Laravel 13 + Breeze (Blade/Tailwind)**
com autenticação completa (login, registro, recuperação de senha, dashboard) e as
telas do projeto descritas a partir do Figma.

> Na página inicial (landing) e no dashboard são exibidos **todos os dados do TCC**
> vindos do banco: a **equipe** (usuários), a **frota** (veículos) e os **protótipos**
> das telas do aplicativo. **CRUD ainda não implementado** — é a próxima etapa do grupo.

---

## Requisitos

| Ferramenta | Versão | Onde verificar |
|-----------|--------|----------------|
| PHP | ≥ 8.2 | `php -v` |
| Composer | ≥ 2.6 | `composer --version` |
| Node.js | ≥ 20 | `node -v` |
| NPM | ≥ 10 | `npm -v` |
| SQLite | (incluso no PHP) | `php -m \| grep -i sqlite` |

> **Nota**: se o PHP da máquina não carregar `pdo_sqlite`/`sqlite3` por padrão
> (distros minimalistas, p.ex. Alpine), os comandos abaixo com este diretório
> já resolvem: o arquivo `php-ext/sqlite.ini` + `PHP_INI_SCAN_DIR` carregam os
> drivers automaticamente. É o padrão usado neste projeto.

## Instalação

```bash
cd site

# 1. Dependências PHP
composer install

# 2. Dependências do front-end
npm install

# 3. Ambiente
cp .env.example .env          # se ainda não existir
php artisan key:generate

# 4. Banco SQLite (o arquivo vazio já existe em database/database.sqlite)
touch database/database.sqlite
php artisan migrate --seed     # cria tabelas + popula com dados de demonstração

# 5. Build do front-end (Tailwind)
npm run build                  # produção
# npm run dev                  # desenvolvimento (Vite HMR)

# 6. Rodar
php artisan serve
# acesse http://localhost:8000
```

> Substitua `php artisan ...` por
> `PHP_INI_SCAN_DIR=$(pwd)/php-ext php artisan ...` se o `php -m` não listar `pdo_sqlite`.

## Contas criadas pelo seeder

Todas com a senha **`veiculotracker123`**:

| Papel | E-mail |
|-------|--------|
| Admin | `admin@tcc.com` |
| Alunos (4) | `aluno1@tcc.com` … `aluno4@tcc.com` |
| Coordenador | `coordenador@tcc.com` |

Dados populados via seeder: **6 usuários**, **12 veículos** (placas Mercosul, SP) e
**12 protótipos de telas** (referências do Figma/Visily).

## O que foi personalizado

| Arquivo | O que faz |
|---------|-----------|
| `routes/web.php` | Rota `home` (landing) e `/dashboard` passando todos os dados do banco |
| `resources/views/welcome.blade.php` | Landing page completa: nome do projeto, especificações, tecnologias, protótipos, frota e equipe — tudo do banco |
| `resources/views/dashboard.blade.php` | Painel autenticado com os protótipos (mockups de celular) + tabela da frota |
| `resources/views/layouts/navigation.blade.php` | Link "Site do TCC" no menu autenticado |
| `database/seeders/` | `UserSeeder`, `VehicleSeeder`, `PrototypeScreenSeeder` (≥ 10 registros) |
| `database/migrations/` | `vehicles`, `prototype_screens`, `role` em `users` |
| `app/Models/` | `Vehicle`, `PrototypeScreen`, `User` com `role` |
| `database/factories/` | Factories de `Vehicle` e `PrototypeScreen` |
| `.env` / `.env.example` | `APP_NAME="VeiculoTracker"`, `DB_CONNECTION=sqlite` |
| `php-ext/sqlite.ini` | Carrega `pdo_sqlite`/`sqlite3` quando a distro não habilita |

## Rotas

| Rota | Acesso | Descrição |
|------|--------|-----------|
| `/` | Público | Landing page com todos os dados do TCC |
| `/login` | Público | Login Breeze |
| `/register` | Público | Registro Breeze |
| `/forgot-password` | Público | Recuperação de senha Breeze |
| `/dashboard` | **Autenticado** | Painel com protótipos e frota |
| `/profile` | Autenticado | Perfil (Breeze) |

## Mapeamento para o TCC

- ✅ **Segunda aplicação + Laravel Breeze** — scaffold oficial com autenticação
- ✅ **Tailwind** — front compilado com Vite
- ✅ **Welcome customizada** — nome do projeto, especificações, tecnologias
- ✅ **Dashboard com protótipos** — telas do Figma renderizadas do banco
- ✅ **Seeder com ≥ 10 registros** — 12 veículos + 12 protótipos + 6 usuários
- ⏳ **CRUD** — próximo passo do grupo (veículos, rotas, protótipos)
- ⏳ **Imagens reais do Figma** — colocar em `public/img/prototipos/` e apontar em `figma_reference`