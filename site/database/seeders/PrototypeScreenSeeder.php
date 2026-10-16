<?php

namespace Database\Seeders;

use App\Models\PrototypeScreen;
use Illuminate\Database\Seeder;

class PrototypeScreenSeeder extends Seeder
{
    /**
     * Telas do app Securitas descritas a partir do conteúdo do Figma/Visily.
     * A demonstração técnica (dashboard) renderiza estes protótipos.
     */
    public function run(): void
    {
        $screens = [
            [
                'name' => 'Login',
                'slug' => 'login',
                'figma_reference' => 'visily-login.png',
                'description' => 'Autenticação do usuário no sistema com e-mail e senha.',
                'endpoint' => 'POST /v1/auth/login',
                'tech' => 'Jetpack Compose + Firebase Auth',
            ],
            [
                'name' => 'Registro',
                'slug' => 'registro',
                'figma_reference' => 'visily-validação.png',
                'description' => 'Criação de conta com validação de campos (e-mail, senha mínima).',
                'endpoint' => 'POST /v1/auth/register',
                'tech' => 'Jetpack Compose + Firebase Auth',
            ],
            [
                'name' => 'Dashboard Principal',
                'slug' => 'dashboard',
                'figma_reference' => 'visily-dashboard-principal.png',
                'description' => 'Painel com resumo da frota: total, ativos e bloqueados, com atalhos rápidos.',
                'endpoint' => 'GET /v1/vehicles',
                'tech' => 'Material 3 + Room SQLite',
            ],
            [
                'name' => 'Rastreamento em Tempo Real',
                'slug' => 'tracking',
                'figma_reference' => 'visily-rastreamento-em-tempo-real.png',
                'description' => 'Google Maps com marcador dinâmico do veículo e telemetria ao vivo (velocidade, lat/lng, bateria).',
                'endpoint' => 'GET /v1/vehicles/:id/telemetry/stream',
                'tech' => 'Google Maps Compose + Realtime DB',
            ],
            [
                'name' => 'Controle Remoto (Bloqueio)',
                'slug' => 'controle-remoto',
                'figma_reference' => 'visily-controle-remoto.png',
                'description' => 'Ação crítica de bloqueio/desbloqueio de ignição com confirmação visual.',
                'endpoint' => 'POST /v1/vehicles/:id/commands',
                'tech' => 'Firebase Realtime DB + SSE',
            ],
            [
                'name' => 'Confirmação de Bloqueio',
                'slug' => 'bloqueio-ok',
                'figma_reference' => 'visily-mobile-preview-(bloqueio).png',
                'description' => 'Feedback visual de que o veículo foi bloqueado com sucesso.',
                'endpoint' => 'SSE event: blocked',
                'tech' => 'Firebase Realtime DB',
            ],
            [
                'name' => 'Histórico de Rotas',
                'slug' => 'historico-rotas',
                'figma_reference' => 'visily-histórico-de-rotas.png',
                'description' => 'Viagens concluídas com data, distância e polyline no mapa (início verde, fim vermelho).',
                'endpoint' => 'GET /v1/routes/:id',
                'tech' => 'Google Maps Polyline + Room',
            ],
            [
                'name' => 'Cadastro de Veículo',
                'slug' => 'cadastro-veiculo',
                'figma_reference' => 'visily-registro-veiculo.png',
                'description' => 'Formulário de placa, marca, modelo, ano e cor com persistência local + nuvem.',
                'endpoint' => 'POST /v1/vehicles',
                'tech' => 'Room + Firestore',
            ],
            [
                'name' => 'Validação de Formulário',
                'slug' => 'validacao-formulario',
                'figma_reference' => 'visily-registro-de-veiculo-erros.png',
                'description' => 'Erros de validação de placa Mercosul/tradicional e de campos obrigatórios.',
                'endpoint' => 'POST /v1/vehicles',
                'tech' => 'Jetpack Compose',
            ],
            [
                'name' => 'Confirmação de Cadastro',
                'slug' => 'cadastro-sucesso',
                'figma_reference' => 'visily-registro-sucesso.png',
                'description' => 'Diálogo de sucesso após gravar o veículo na frota.',
                'endpoint' => 'POST /v1/vehicles',
                'tech' => 'Room + Firestore',
            ],
            [
                'name' => 'Linkagem de API',
                'slug' => 'link-agamento-api',
                'figma_reference' => 'visily-linkagem-da-api.png',
                'description' => 'Configuração de endpoints e chaves de API para rastreadores IoT (Teltonika, OBD-II).',
                'endpoint' => 'GET /v1/hardware/:deviceId/status',
                'tech' => 'Node.js + Express + SQLite',
            ],
            [
                'name' => 'Configurações',
                'slug' => 'configuracoes',
                'figma_reference' => 'visily-configurações.png',
                'description' => 'Tema claro/escuro, intervalo de atualização do GPS, notificações e logout.',
                'endpoint' => 'local (app)',
                'tech' => 'Material 3 + SharedPreferences',
            ],
        ];

        foreach ($screens as $i => $screen) {
            PrototypeScreen::updateOrCreate(
                ['slug' => $screen['slug']],
                ['order_column' => $i + 1] + $screen
            );
        }
    }
}