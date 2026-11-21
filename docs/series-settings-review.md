# Séries e Configurações — revisão

Alterações preparadas na branch `nexus/series-settings`, a partir da versão de Filmes aprovada (`nexus/movie-browser`).

- Séries reutiliza a tela de Filmes: categorias na lateral, pesquisa, ordenação, favoritos, pôsteres e seleção do conteúdo.
- Configurações tem menu lateral preto e dourado, com conteúdo rolável.
- Meu plano consulta assinatura real e mostra início, vencimento com horário de Brasília e tempo restante.
- Reprodução permite ativar/desativar retomada da posição; áudio e legendas seguem disponíveis no player.
- Conexão mede download com amostra de 5 MB, resposta da API e uma amostra de canal, filme e série autorizada pelo servidor. Não envia tokens de login ao fornecedor.
- Armazenamento apaga apenas cache de imagens mediante confirmação. Preserva preferências, login, favoritos, histórico e PIN.
- Controle parental mantém o fluxo existente.
- Meu dispositivo exibe o aparelho e consulta o vínculo real. Trocas são administrativas.
- Suporte permite copiar diagnóstico sem tokens, senhas, URLs dos streams ou identificadores pessoais. Contém orientações rápidas. WhatsApp depende do número de atendimento.

## Validação

A suíte verifica compilação Android, testes unitários, reprodução com ExoPlayer e VLC, seleção de uma série e preservação de favoritos ao apagar cache. O cadastro no servidor foi verificado com rollback: primeiro aparelho aceito, repetição aceita, segundo aparelho recusado, revogação respeitada e usuário anônimo recusado.

O identificador deriva do Android ID por aplicativo; copiar preferências antigas em um backup não copia a autorização para outro aparelho.

## Operação

Aplique `supabase/single-device-account.sql` antes de ativar `nexus_device_checks_enabled()`. Remova apenas os quatro registros antigos de teste do Redmi antes da ativação, para que o primeiro acesso do novo APK vincule o aparelho atual. Trocas posteriores são administrativas. A RPC exige assinatura válida e não permite substituir outro aparelho ativo nem reativar um vínculo revogado.

O botão de WhatsApp depende da definição do número de atendimento. O suporte já permite copiar diagnóstico sem dados de autenticação.
