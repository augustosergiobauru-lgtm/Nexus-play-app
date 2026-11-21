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

## Validação e pendências

`git diff --check` passou. A compilação local foi impedida pelo bloqueio de acesso à distribuição Gradle e pela ausência do SDK Android. Ainda é necessário executar o Build no GitHub e testar o APK.

O servidor segue em modo de teste. O arquivo `supabase/single-device-account.sql` prepara cadastro inicial com assinatura válida e limite de um aparelho. Ele ainda não foi aplicado: a ativação exige coordenar o novo APK e resolver quatro vínculos antigos de teste do Redmi, preservando a instalação real. A RPC não permite substituir um aparelho ativo ou reativar identificadores revogados por conta própria.

O envio da branch ao GitHub foi bloqueado pela revisão automática. Nenhuma alteração foi publicada e nenhum APK novo foi gerado nesta etapa.
