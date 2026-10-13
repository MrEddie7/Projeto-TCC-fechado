<x-app-layout>
    <x-slot name="header">
        <h2 class="font-semibold text-xl text-gray-800 leading-tight">
            {{ __('Painel do Sistema — VeiculoTracker (Securitas)') }}
        </h2>
    </x-slot>

    <div class="py-12">
        <div class="max-w-7xl mx-auto sm:px-6 lg:px-8 space-y-10">

            {{-- Resumo da frota extraído do banco (seeder) --}}
            <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
                <div class="bg-white overflow-hidden shadow-sm sm:rounded-lg p-5">
                    <p class="text-xs uppercase tracking-wider text-gray-400">Veículos na frota</p>
                    <p class="mt-1 text-3xl font-bold text-gray-900">{{ $stats['vehicles'] }}</p>
                </div>
                <div class="bg-white overflow-hidden shadow-sm sm:rounded-lg p-5">
                    <p class="text-xs uppercase tracking-wider text-gray-400">Bloqueados</p>
                    <p class="mt-1 text-3xl font-bold text-rose-600">{{ $stats['blockedVehicles'] }}</p>
                </div>
                <div class="bg-white overflow-hidden shadow-sm sm:rounded-lg p-5">
                    <p class="text-xs uppercase tracking-wider text-gray-400">Protótipos (Figma)</p>
                    <p class="mt-1 text-3xl font-bold text-gray-900">{{ $stats['screens'] }}</p>
                </div>
                <div class="bg-white overflow-hidden shadow-sm sm:rounded-lg p-5">
                    <p class="text-xs uppercase tracking-wider text-gray-400">Membros da equipe</p>
                    <p class="mt-1 text-3xl font-bold text-gray-900">{{ $stats['members'] }}</p>
                </div>
            </div>

            {{-- Demonstração técnica: os protótipos do TCC --}}
            <section>
                <div class="flex items-center justify-between">
                    <div>
                        <h3 class="text-lg font-semibold text-gray-900">Demonstração técnica — protótipos do TCC</h3>
                        <p class="text-sm text-gray-500">Telas do aplicativo baseadas no conteúdo do Figma, renderizadas a partir do banco de dados.</p>
                    </div>
                    <a href="{{ route('home') }}" class="text-sm font-medium text-blue-600 hover:text-blue-800">← Página inicial</a>
                </div>

                <div class="mt-6 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
                    @forelse ($screens as $screen)
                        <div class="bg-white overflow-hidden shadow-sm sm:rounded-lg">
                            {{-- Moldura de celular (simulação do protótipo) --}}
                            <div class="flex justify-center border-b border-gray-100 bg-gradient-to-b from-indigo-50 to-white p-5">
                                <div class="h-44 w-28 rounded-[1.4rem] border-4 border-gray-800 bg-white p-2 shadow-lg">
                                    <div class="flex h-full flex-col overflow-hidden rounded-lg bg-slate-900">
                                        <div class="flex items-center gap-1.5 px-2 pt-2">
                                            <span class="h-1.5 w-1.5 rounded-full bg-rose-500"></span>
                                            <span class="h-1.5 w-1.5 rounded-full bg-amber-400"></span>
                                            <span class="h-1.5 w-1.5 rounded-full bg-emerald-500"></span>
                                            <span class="ml-auto h-1 w-6 rounded bg-white/20"></span>
                                        </div>
                                        <div class="flex flex-1 flex-col items-center justify-center gap-1.5 px-2 text-center">
                                            <span class="h-2 w-2 rounded-full bg-emerald-400"></span>
                                            <span class="line-clamp-2 text-[10px] font-semibold leading-tight text-white">{{ $screen->name }}</span>
                                            <span class="text-[8px] text-slate-400">{{ $screen->tech ?? 'App Android' }}</span>
                                        </div>
                                        <div class="mb-1.5 flex justify-center"><span class="h-1 w-8 rounded-full bg-white/25"></span></div>
                                    </div>
                                </div>
                            </div>

                            <div class="p-5">
                                <div class="flex items-center justify-between gap-2">
                                    <h4 class="text-sm font-semibold text-gray-900">{{ $screen->name }}</h4>
                                    <span class="rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700">pronto</span>
                                </div>
                                <p class="mt-1.5 text-sm leading-relaxed text-gray-600">{{ $screen->description }}</p>
                                <dl class="mt-3 space-y-1 text-xs">
                                    @if ($screen->figma_reference)
                                        <div class="flex justify-between gap-2">
                                            <dt class="text-gray-400">Figma</dt>
                                            <dd class="font-mono text-gray-600">{{ $screen->figma_reference }}</dd>
                                        </div>
                                    @endif
                                    @if ($screen->endpoint)
                                        <div class="flex justify-between gap-2">
                                            <dt class="text-gray-400">Endpoint</dt>
                                            <dd class="font-mono text-gray-600">{{ $screen->endpoint }}</dd>
                                        </div>
                                    @endif
                                </dl>
                            </div>
                        </div>
                    @empty
                        <div class="col-span-full rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500">
                            Nenhum protótipo cadastrado. Rode <code class="font-mono text-gray-700">php artisan db:seed</code>.
                        </div>
                    @endforelse
                </div>
            </section>

            {{-- Frota --}}
            <section>
                <h3 class="text-lg font-semibold text-gray-900">Frota no banco de dados</h3>
                <div class="mt-4 overflow-hidden bg-white shadow-sm sm:rounded-lg">
                    <table class="min-w-full divide-y divide-gray-200 text-sm">
                        <thead class="bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                            <tr>
                                <th class="px-5 py-3">Placa</th>
                                <th class="px-5 py-3">Veículo</th>
                                <th class="px-5 py-3">Ano</th>
                                <th class="px-5 py-3">Velocidade</th>
                                <th class="px-5 py-3">Status</th>
                            </tr>
                        </thead>
                        <tbody class="divide-y divide-gray-100">
                            @forelse ($vehicles as $vehicle)
                                <tr class="hover:bg-gray-50">
                                    <td class="px-5 py-3 font-mono font-semibold text-gray-900">{{ $vehicle->plate }}</td>
                                    <td class="px-5 py-3 text-gray-600">{{ $vehicle->brand }} {{ $vehicle->model }} · {{ $vehicle->color }}</td>
                                    <td class="px-5 py-3 text-gray-600">{{ $vehicle->year }}</td>
                                    <td class="px-5 py-3 text-gray-600">{{ $vehicle->speed }} km/h</td>
                                    <td class="px-5 py-3">
                                        @if ($vehicle->is_blocked)
                                            <span class="rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-semibold text-rose-700">Bloqueado</span>
                                        @else
                                            <span class="rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">Em circulação</span>
                                        @endif
                                    </td>
                                </tr>
                            @empty
                                <tr>
                                    <td colspan="5" class="px-5 py-6 text-center text-gray-500">Nenhum veículo cadastrado.</td>
                                </tr>
                            @endforelse
                        </tbody>
                    </table>
                </div>
            </section>

            {{-- Próximos passos --}}
            <section class="rounded-lg border border-blue-100 bg-blue-50 p-6">
                <h3 class="font-semibold text-blue-900">Próximos passos do grupo</h3>
                <ul class="mt-2 list-inside list-disc space-y-1 text-sm text-blue-800">
                    <li>Criar o CRUD de veículos com as mesmas telas do protótipo (Login, Cadastro, Dashboard).</li>
                    <li>Integrar as rotas com os dados da API do backend do TCC (quando definida no grupo).</li>
                    <li>Adicionar as capturas reais do Figma (imagens) em <code class="font-mono">public/img/prototipos/</code> e apontar pela coluna <code class="font-mono">figma_reference</code>.</li>
                </ul>
            </section>
        </div>
    </div>
</x-app-layout>