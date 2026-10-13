<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="description" content="VeiculoTracker (Securitas) — sistema de rastreamento veicular, telemetria, bloqueio remoto e gestão de rotas. Projeto de TCC.">

        <title>{{ config('app.name', 'Laravel') }} — Painel do TCC</title>

        @fonts

        @vite(['resources/css/app.css', 'resources/js/app.js'])
    </head>
    <body class="bg-slate-950 text-slate-200 antialiased selection:bg-blue-500/30 selection:text-white">
        <div class="min-h-screen flex flex-col">

            {{-- ── Navegação ─────────────────────────────────────────────── --}}
            <header class="sticky top-0 z-40 border-b border-white/10 bg-slate-950/80 backdrop-blur">
                <nav class="mx-auto flex max-w-6xl items-center justify-between px-5 py-3.5">
                    <a href="{{ route('home') }}" class="flex items-center gap-2.5 font-semibold text-white">
                        <span class="grid h-8 w-8 place-items-center rounded-lg bg-blue-500 text-slate-950">VT</span>
                        <span class="text-sm tracking-tight">VeiculoTracker <span class="text-blue-400">· Securitas</span></span>
                    </a>

                    <div class="flex items-center gap-2">
                        @auth
                            <a href="{{ route('dashboard') }}" class="rounded-lg bg-blue-500 px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-blue-400">
                                Dashboard
                            </a>
                        @else
                            <a href="{{ route('login') }}" class="rounded-lg border border-white/15 px-4 py-2 text-sm font-medium text-slate-200 transition hover:border-white/30 hover:text-white">
                                Entrar
                            </a>
                            @if (Route::has('register'))
                                <a href="{{ route('register') }}" class="rounded-lg bg-blue-500 px-4 py-2 text-sm font-semibold text-slate-950 transition hover:bg-blue-400">
                                    Criar conta
                                </a>
                            @endif
                        @endauth
                    </div>
                </nav>
            </header>

            <main class="flex-1">

                {{-- ── Hero ───────────────────────────────────────────────── --}}
                <section class="border-b border-white/10 bg-gradient-to-b from-blue-950/60 via-slate-950 to-slate-950">
                    <div class="mx-auto max-w-6xl px-5 py-16 lg:py-24">
                        <p class="mb-4 inline-flex items-center gap-2 rounded-full border border-blue-400/30 bg-blue-500/10 px-3 py-1 text-xs font-medium text-blue-300">
                            <span class="h-1.5 w-1.5 rounded-full bg-emerald-400"></span>
                            Projeto de TCC — desenvolvimento em grupo
                        </p>
                        <h1 class="max-w-3xl text-4xl font-bold leading-tight tracking-tight text-white lg:text-5xl">
                            Rastreamento veicular, telemetria e <span class="text-blue-400">bloqueio remoto</span> em tempo real.
                        </h1>
                        <p class="mt-5 max-w-2xl text-base leading-relaxed text-slate-400">
                            O <strong class="text-slate-200">VeiculoTracker</strong> é um sistema móvel (Android nativo)
                            que monitora a localização da frota sobre o Google Maps, emite comandos de corte de ignição
                            e analisa rotas históricas — com arquitetura offline-first e sincronização contínua com a nuvem (Firebase).
                        </p>

                        <div class="mt-8 flex flex-wrap gap-3">
                            @auth
                                <a href="{{ route('dashboard') }}" class="rounded-lg bg-blue-500 px-5 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-blue-400">
                                    Ver demonstração técnica →
                                </a>
                            @else
                                <a href="{{ route('register') }}" class="rounded-lg bg-blue-500 px-5 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-blue-400">
                                    Criar conta e acessar o painel
                                </a>
                                <a href="{{ route('login') }}" class="rounded-lg border border-white/15 px-5 py-2.5 text-sm font-medium text-slate-200 transition hover:border-white/30 hover:text-white">
                                    Já tenho conta
                                </a>
                            @endauth
                        </div>

                        <dl class="mt-12 grid grid-cols-2 gap-4 sm:grid-cols-4">
                            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
                                <dt class="text-xs uppercase tracking-wider text-slate-500">Frota</dt>
                                <dd class="mt-1 text-2xl font-bold text-white">{{ $stats['vehicles'] }}</dd>
                                <dd class="text-xs text-slate-500">veículos demonstrativos</dd>
                            </div>
                            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
                                <dt class="text-xs uppercase tracking-wider text-slate-500">Bloqueados</dt>
                                <dd class="mt-1 text-2xl font-bold text-rose-400">{{ $stats['blockedVehicles'] }}</dd>
                                <dd class="text-xs text-slate-500">com ignição cortada</dd>
                            </div>
                            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
                                <dt class="text-xs uppercase tracking-wider text-slate-500">Telas</dt>
                                <dd class="mt-1 text-2xl font-bold text-white">{{ $stats['screens'] }}</dd>
                                <dd class="text-xs text-slate-500">protótipos do Figma</dd>
                            </div>
                            <div class="rounded-xl border border-white/10 bg-white/5 p-4">
                                <dt class="text-xs uppercase tracking-wider text-slate-500">Equipe</dt>
                                <dd class="mt-1 text-2xl font-bold text-white">{{ $stats['members'] }}</dd>
                                <dd class="text-xs text-slate-500">membros do grupo</dd>
                            </div>
                        </dl>
                    </div>
                </section>

                {{-- ── Sobre / especificações ─────────────────────────────── --}}
                <section class="border-b border-white/10">
                    <div class="mx-auto max-w-6xl px-5 py-14">
                        <div class="grid gap-10 lg:grid-cols-2">
                            <div>
                                <h2 class="text-2xl font-bold text-white">Especificações do projeto</h2>
                                <p class="mt-3 text-sm leading-relaxed text-slate-400">
                                    Sistema com arquitetura MVVM + Clean Architecture: banco SQLite interno (Room) como fonte
                                    imediata da verdade, Firebase Realtime Database para telemetria e comandos de baixa
                                    latência, e Cloud Firestore para persistência distribuída de cadastros e históricos.
                                </p>
                                <ul class="mt-6 space-y-2.5 text-sm text-slate-300">
                                    <li class="flex gap-2.5"><span class="text-blue-400">◈</span> Rastreamento em tempo real sobre Google Maps</li>
                                    <li class="flex gap-2.5"><span class="text-blue-400">◈</span> Bloqueio/desbloqueio remoto da ignição (anti-furto)</li>
                                    <li class="flex gap-2.5"><span class="text-blue-400">◈</span> Histórico de rotas com métricas de velocidade e distância</li>
                                    <li class="flex gap-2.5"><span class="text-blue-400">◈</span> Operação 100% offline com sincronização contínua</li>
                                    <li class="flex gap-2.5"><span class="text-blue-400">◈</span> API de ponte (Node.js + Express) para rastreadores IoT</li>
                                </ul>
                            </div>

                            <div>
                                <h2 class="text-2xl font-bold text-white">Tecnologias</h2>
                                <div class="mt-6 grid gap-3 sm:grid-cols-2">
                                    @php
                                        $techs = [
                                            ['Kotlin + Jetpack Compose', 'Interface declarativa e Material Design 3'],
                                            ['Room + SQLite', 'Banco local offline-first'],
                                            ['Firebase', 'Auth, Realtime Database e Cloud Firestore'],
                                            ['Google Maps SDK', 'Mapas, marcadores e polylines'],
                                            ['Node.js + Express', 'Backend/API de ponte com hardware'],
                                            ['Laravel Breeze', 'Este site institucional e painel do grupo'],
                                        ];
                                    @endphp
                                    @foreach ($techs as [$name, $desc])
                                        <div class="rounded-xl border border-white/10 bg-white/5 p-4">
                                            <p class="text-sm font-semibold text-white">{{ $name }}</p>
                                            <p class="mt-1 text-xs text-slate-500">{{ $desc }}</p>
                                        </div>
                                    @endforeach
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                {{-- ── Protótipos ─────────────────────────────────────────── --}}
                <section class="border-b border-white/10 bg-slate-900/40">
                    <div class="mx-auto max-w-6xl px-5 py-14">
                        <div class="flex items-end justify-between gap-4">
                            <div>
                                <h2 class="text-2xl font-bold text-white">Protótipos do sistema</h2>
                                <p class="mt-2 text-sm text-slate-400">Telas do aplicativo desenhadas no Figma/Visily — a demonstração visual do TCC.</p>
                            </div>
                            <span class="rounded-full border border-white/10 px-3 py-1 text-xs text-slate-400">{{ $screens->count() }} telas</span>
                        </div>

                        <div class="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                            @forelse ($screens as $screen)
                                <article class="group flex flex-col rounded-xl border border-white/10 bg-white/5 p-4 transition hover:border-blue-400/40 hover:bg-white/[0.07]">
                                    <div class="flex items-center justify-between gap-3">
                                        <span class="text-xs font-semibold uppercase tracking-wider text-blue-300">{{ $screen->name }}</span>
                                        <span class="h-2 w-2 shrink-0 rounded-full bg-emerald-400" title="Protótipo"></span>
                                    </div>
                                    @if ($screen->figma_reference)
                                        <p class="mt-3 text-[11px] font-mono text-slate-500">Figma: {{ $screen->figma_reference }}</p>
                                    @endif
                                    <p class="mt-1.5 text-sm leading-relaxed text-slate-300">{{ $screen->description }}</p>
                                    <div class="mt-auto pt-3 text-xs">
                                        @if ($screen->endpoint)
                                            <code class="rounded bg-black/30 px-1.5 py-0.5 font-mono text-[11px] text-emerald-300">{{ $screen->endpoint }}</code>
                                        @endif
                                        @if ($screen->tech)
                                            <span class="ml-1 text-slate-500">· {{ $screen->tech }}</span>
                                        @endif
                                    </div>
                                </article>
                            @empty
                                <p class="text-sm text-slate-500">Execute <code class="font-mono text-slate-300">php artisan db:seed</code> para popular os protótipos.</p>
                            @endforelse
                        </div>
                    </div>
                </section>

                {{-- ── Frota ──────────────────────────────────────────────── --}}
                <section class="border-b border-white/10">
                    <div class="mx-auto max-w-6xl px-5 py-14">
                        <h2 class="text-2xl font-bold text-white">Frota de demonstração</h2>
                        <p class="mt-2 text-sm text-slate-400">Veículos cadastrados nos seeders, prontos para o futuro CRUD.</p>

                        <div class="mt-8 overflow-x-auto rounded-xl border border-white/10">
                            <table class="w-full text-left text-sm">
                                <thead class="bg-white/5 text-xs uppercase tracking-wider text-slate-500">
                                    <tr>
                                        <th class="px-4 py-3">Placa</th>
                                        <th class="px-4 py-3">Veículo</th>
                                        <th class="px-4 py-3">Ano</th>
                                        <th class="px-4 py-3">Cor</th>
                                        <th class="px-4 py-3">Velocidade</th>
                                        <th class="px-4 py-3">Status</th>
                                    </tr>
                                </thead>
                                <tbody class="divide-y divide-white/5">
                                    @forelse ($vehicles as $vehicle)
                                        <tr class="transition hover:bg-white/5">
                                            <td class="px-4 py-3 font-mono font-semibold text-white">{{ $vehicle->plate }}</td>
                                            <td class="px-4 py-3 text-slate-300">{{ $vehicle->brand }} {{ $vehicle->model }}</td>
                                            <td class="px-4 py-3 text-slate-400">{{ $vehicle->year }}</td>
                                            <td class="px-4 py-3 text-slate-400">{{ $vehicle->color }}</td>
                                            <td class="px-4 py-3 text-slate-300">{{ $vehicle->speed }} km/h</td>
                                            <td class="px-4 py-3">
                                                @if ($vehicle->is_blocked)
                                                    <span class="rounded-full bg-rose-500/15 px-2.5 py-0.5 text-xs font-semibold text-rose-400">Bloqueado</span>
                                                @else
                                                    <span class="rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-xs font-semibold text-emerald-400">Em circulação</span>
                                                @endif
                                            </td>
                                        </tr>
                                    @empty
                                        <tr>
                                            <td colspan="6" class="px-4 py-6 text-center text-slate-500">Nenhum veículo cadastrado.</td>
                                        </tr>
                                    @endforelse
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>

                {{-- ── Equipe ─────────────────────────────────────────────── --}}
                <section class="border-b border-white/10 bg-slate-900/40">
                    <div class="mx-auto max-w-6xl px-5 py-14">
                        <h2 class="text-2xl font-bold text-white">Equipe do projeto</h2>
                        <p class="mt-2 text-sm text-slate-400">Contas criadas pelo seeder para o desenvolvimento em grupo.</p>
                        <div class="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                            @forelse ($users as $user)
                                <div class="flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 p-4">
                                    <span class="grid h-9 w-9 place-items-center rounded-full bg-blue-500/20 text-sm font-bold text-blue-300">
                                        {{ strtoupper(mb_substr($user->name, 0, 1)) }}
                                    </span>
                                    <div>
                                        <p class="text-sm font-semibold text-white">{{ $user->name }}</p>
                                        <p class="text-xs text-slate-500">{{ $user->email }} · {{ ucfirst($user->role) }}</p>
                                    </div>
                                </div>
                            @empty
                                <p class="text-sm text-slate-500">Execute <code class="font-mono text-slate-300">php artisan db:seed</code>.</p>
                            @endforelse
                        </div>
                    </div>
                </section>

                {{-- ── CTA final ──────────────────────────────────────────── --}}
                <section>
                    <div class="mx-auto max-w-6xl px-5 py-16 text-center">
                        <h2 class="text-2xl font-bold text-white">Próxima etapa do grupo</h2>
                        <p class="mx-auto mt-3 max-w-xl text-sm text-slate-400">
                            A base institucional e o painel autenticado estão prontos. O próximo passo é o CRUD de
                            veículos, rotas e protótipos direto pelas telas do Laravel.
                        </p>
                        @auth
                            <a href="{{ route('dashboard') }}" class="mt-6 inline-block rounded-lg bg-blue-500 px-6 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-blue-400">
                                Abrir o painel
                            </a>
                        @else
                            <a href="{{ route('register') }}" class="mt-6 inline-block rounded-lg bg-blue-500 px-6 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-blue-400">
                                Criar conta do grupo
                            </a>
                        @endauth
                    </div>
                </section>
            </main>

            <footer class="border-t border-white/10 py-6">
                <div class="mx-auto max-w-6xl px-5 text-center text-xs text-slate-500">
                    VeiculoTracker · Securitas — TCC · Laravel {{ Illuminate\Foundation\Application::VERSION }} / PHP {{ PHP_VERSION }}
                </div>
            </footer>
        </div>
    </body>
</html>