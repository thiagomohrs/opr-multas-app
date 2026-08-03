# OPR (Overruled Peer Review) Multas App

Sistema full-stack em um único projeto **Spring Boot 3 (Java 21) + Thymeleaf**, com moderação de casos por reputação (OPR) e login via formulário ou **Google OAuth2**.

## 🚀 Tecnologias

- **Java 21**
- **Spring Boot 3.2.0** (Web MVC + Thymeleaf)
- **Spring Security** (form login + OAuth2 Client)
- **Spring Data JPA**
- **Spring Cache** (`@Cacheable`/`@CacheEvict`) com **Redis (Upstash/Vercel KV)** em produção e **Caffeine** em dev
- **H2 Database** (desenvolvimento) / **PostgreSQL** (produção)
- **Lombok**
- **Maven**

## 📋 Pré-requisitos

- Java 21 ou superior
- Maven 3.6+

## 🔧 Execução

```powershell
# Na raiz do projeto:
.\start-backend.ps1
```

O script ativa o perfil `dev` (habilita o console H2 em `/h2-console` e desativa o cache do Thymeleaf).

Ou diretamente:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"   # opcional
mvn spring-boot:run
```

Acesse: **http://localhost:8080**

> **Nota sobre o Maven:** se o `settings.xml` global (`~/.m2/settings.xml`) apontar para um mirror corporativo inacessível, copie/ajuste um settings apontando para o Maven Central e salve como `maven-settings.xml` na raiz do projeto. O script o usará automaticamente. Exemplo:
>
> ```xml
> <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
>   <mirrors>
>     <mirror>
>       <id>central-direct</id>
>       <name>Maven Central</name>
>       <url>https://repo.maven.apache.org/maven2</url>
>       <mirrorOf>central</mirrorOf>
>     </mirror>
>   </mirrors>
> </settings>
> ```

## 👤 Usuários de Teste

Criados pelo `DataInitializer` no primeiro boot (passwords padrão; podem ser sobrescritas via env `OPR_SEED_*_SENHA`):

| Usuário   | Senha        | Role  | Score | Revisor |
|-----------|--------------|-------|-------|---------|
| `admin`   | `admin123`   | ADMIN | 150   | Sim     |
| `revisor` | `revisor123` | USER  | 130   | Sim     |
| `revisor2`| `revisor123` | USER  | 110   | Sim     |
| `revisor3`| `revisor123` | USER  | 105   | Sim     |
| `user`    | `user123`    | USER  | 0     | Não     |

O seed também cria 7 casos de demonstração (`DEMO-1001`…`DEMO-7007`) cobrindo os status da fila — aguardando, em votação, aprovada, rejeitada, expirada e maliciosa — além de **1 anexo (evidência)** no caso `DEMO-1001`.

## 🔐 Google OAuth2

1. Crie um projeto no [Google Cloud Console](https://console.cloud.google.com/).
2. Configure as credenciais OAuth (URI de redirecionamento: `http://localhost:8080/login/oauth2/code/google`).
3. Defina as variáveis de ambiente (ou edite `application.properties`):

```powershell
$env:GOOGLE_CLIENT_ID = "seu_client_id"
$env:GOOGLE_CLIENT_SECRET = "seu_client_secret"
```

Ao entrar com Google, o usuário é criado automaticamente (score 0) ou vinculado a um usuário existente com o mesmo e-mail (herdando a role, ex.: `admin@opr.com`).

## 🛡️ Sistema OPR (Moderação por Reputação)

As "multas" são os casos moderados pela comunidade de revisores.

### Fluxo

```
[Solicitante] ──submete──▶ [AGUARDANDO_REVISAO] ──▶ [EM_VOTACAO] ──▶ [APROVADA | REJEITADA]
                                                                 └──▶ [EXPIRADA] (prazo esgotado)
```

- O **peso do voto** é `score_do_revisor / limiar_revisor`.
- O caso é resolvido quando o **número de votos** atinge o quórum (`votosNecessarios`), decidindo pela maioria dos pesos.
- **Feedback de score:** solicitante `+10` (aprovado) / `-5` (rejeitado); revisor `+5` (voto alinhado) / `-3` (divergente); `-15` para revisor que aprovou caso marcado como malicioso pelo ADMIN.
- **Proteções:** cooldown (1 voto por caso), autoexclusão do solicitante, score mínimo 0, e job agendado que expira casos sem quórum.

### Rotas

| Rota | Acesso | Função |
|---|---|---|
| `/multas` | Autenticado | Listagem/CRUD de multas (edição ADMIN) |
| `/revisao` | Revisor ou ADMIN | Fila de casos pendentes |
| `/revisao/{id}` | Revisor ou ADMIN | Detalhe do caso + formulário de voto |
| `/meu-score` | Autenticado | Score e histórico de reputação |
| `/admin/moderacao` | ADMIN | Dashboard + flag de caso malicioso |

### Configuração

```properties
opr.moderacao.limiar-revisor=100
opr.moderacao.votos-necessarios=3
opr.moderacao.prazo-revisao-horas=72
opr.score.caso-aprovado-solicitante=10
opr.score.caso-rejeitado-solicitante=-5
opr.score.voto-correto-revisor=5
opr.score.voto-incorreto-revisor=-3
opr.score.voto-malicioso-revisor=-15
```

## 📎 Anexos / Evidências

- Upload de **até 10 MB** por arquivo (`spring.servlet.multipart.max-file-size=10MB`), múltiplos arquivos por caso.
- Formatos aceitos por content-type **e** extensão: imagens (`jpg jpeg png gif webp bmp svg`) e vídeos (`mp4 webm mov m4v mkv avi`).
- As evidências aparecem na galeria do detalhe do caso e na página de revisão (`<img>`/`<video>`); ADMIN/revisor podem remover anexos.

## 🩺 Health Check

- `GET /api/health` é público e responde `{"status":"UP"}` — usado para verificar se a aplicação subiu (ex.: no deploy da Vercel).

## ⚡ Cache de consultas (Redis / Caffeine)

Os dashboards de **multas**, **fila de revisão**, **casos resolvidos** e **moderação** consultam o banco a cada requisição. Para reduzir carga/repetição, esses resultados são cacheados:

| Cache (`CacheConfig`) | Método | Invalidado em |
|---|---|---|
| `multas` | `MultaService.listarTodas()` | criar/atualizar/deletar/remover anexo |
| `filaRevisao` | `ModeracaoService.listarFilaRevisao()` | registrar voto/resolver/expirar/flag |
| `casosResolvidos` | `ModeracaoService.listarCasosResolvidos()` | registrar voto/resolver/expirar/flag |
| `moderacaoCasos` | `ModeracaoService.listarTodosCasos()` | registrar voto/resolver/expirar/flag |

**O que é cacheado:** apenas **DTOs** (`MultaDto`/`UsuarioDto`) serializáveis — nunca entidades JPA (evita lazy-loading e `LazyInitializationException` com `open-in-view=false`). Os DTOs espelham os getters usados pelas templates, então **nenhuma view precisa mudar**.

**Seleção automática (`CacheConfig`):**

- Se `REDIS_URL` estiver definida → **Redis/Upstash** (`RedisCacheManager` + serialização JSON com suporte a `LocalDateTime`, `BigDecimal` e enums; TLS ativo para `rediss://` ou hosts `.upstash.io`).
- Se `REDIS_URL` ausente → **Caffeine** em memória (dev/local).

Um `CacheErrorHandler` customizado garante que **falhas de cache nunca geram erro/500**: o caso cai numa leitura normal do banco (basta um `GET`/`PUT` falhar) e, se uma invalidação falhar, o `TTL` cobre a consistência.

**Configuração:**

```properties
app.cache.redis-url=${REDIS_URL:}   # se vazio -> Caffeine
app.cache.ttl=30s
app.cache.max-size=10000
```

> **Importante:** a Upstash exige TLS mesmo com link `redis://`. O `CacheConfig` detecta por `rediss` ou host `.upstash` e liga SSL automaticamente — use a URL inteira fornecida no painel (ex. `redis://default:TOKEN@xxx.upstash.io:6379`).

## 🗄️ Banco de Dados

H2 em memória por padrão (dados são perdidos ao reiniciar). Para PostgreSQL em produção, ative o perfil `prod` (driver e dialeto são detectados automaticamente):

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
# Aceita o formato nativo do Supabase (convertido para JDBC automaticamente):
$env:DATABASE_URL = "postgresql://usuario:senha@db.xxxx.supabase.co:5432/postgres"
# ou formato JDBC:
# $env:DATABASE_URL = "jdbc:postgresql://host:5432/multasdb"
$env:DATABASE_USER = "seu_usuario"       # opcional se a URL já tiver credenciais
$env:DATABASE_PASSWORD = "sua_senha"     # opcional se a URL já tiver credenciais
```

O seed de demonstração roda por padrão também em `prod` (para o primeiro acesso criar `admin`). Ele executa **após** o boot (`ApplicationReadyEvent`), em thread própria e com tolerância a falhas — se o banco estiver indisponível, o container continua no ar e o problema é apenas logado. Para desativar: `OPR_SEED_DEMO_DATA=false`. As senhas do seed vêm de env vars (`OPR_SEED_ADMIN_SENHA`, `OPR_SEED_USER_SENHA`, `OPR_SEED_REVISOR_SENHA`) — nunca hardcode em produção.

## 🚀 Deploy na Vercel

A Vercel suporta aplicações Spring Boot através de um container (`Dockerfile.vercel`), rodando no **Fluid Compute**.

### Pré-requisitos

- Conta na [Vercel](https://vercel.com) e [CLI da Vercel](https://vercel.com/docs/cli) (`npm i -g vercel`).
- O container precisa ouvir na porta da variável `PORT` (já configurado em `application.properties` via `server.port=${PORT:8080}`).

### Passos

```powershell
# Na raiz do projeto:
vercel login
vercel
```

A Vercel detecta o `Dockerfile.vercel`, builda a imagem e publica. Cada `git push` gera um preview.

### Variáveis de ambiente

Configure no dashboard (Project Settings → Environment Variables) ou via `vercel env add`:

| Variável | Obrigatória | Descrição |
|---|---|---|
| `GOOGLE_CLIENT_ID` | Não* | Client ID do Google OAuth2 |
| `GOOGLE_CLIENT_SECRET` | Não* | Client Secret do Google OAuth2 |
| `SPRING_PROFILES_ACTIVE` | Não | `prod` para usar PostgreSQL (já definido no Dockerfile.vercel) |
| `DATABASE_URL` | Se `prod` | URL do PostgreSQL: `postgresql://...` (Supabase) ou `jdbc:postgresql://...` |
| `DATABASE_USER` | Se `prod` | Usuário do banco (opcional se a URL tiver credenciais) |
| `DATABASE_PASSWORD` | Se `prod` | Senha do banco (opcional se a URL tiver credenciais) |
| `REDIS_URL` | Não | URL do Redis/Upstash (`redis://default:TOKEN@host:6379`). Se ausente, usa cache em memória (Caffeine) |
| `OPR_SEED_ADMIN_SENHA` | Não | Senha do usuário `admin` criado pelo seed (default `admin123`) |
| `OPR_SEED_USER_SENHA` | Não | Senha do usuário `user` criado pelo seed (default `user123`) |
| `OPR_SEED_REVISOR_SENHA` | Não | Senha dos revisores `revisor`, `revisor2`, `revisor3` (default `revisor123`) |
| `OPR_SEED_DEMO_DATA` | Não | `false` para desativar o seed de demonstração em produção |
| `OPR_DDL_AUTO` | Primeiro deploy | `update` para o app criar o schema no banco (ver abaixo) |

\* Sem as credenciais Google, o login por formulário continua funcionando.

> **Primeiro deploy — criar o schema:** em `prod` o `ddl-auto` é `none` por padrão (para manter o cold-start abaixo do limite da Vercel). No **primeiro** deploy, defina `OPR_DDL_AUTO=update` na Vercel, faça o deploy e, depois que o schema existir, **remova essa variável** (ou volte para `none`). Sem esse passo, o seed roda em background mas falha com `Table "USUARIOS" not found` — a app sobe (health UP), mas o login/rotas falham.

> **Importante:** ao usar Google OAuth2, adicione `https://<seu-projeto>.vercel.app/login/oauth2/code/google` como URI de redirecionamento no Google Cloud Console.

### Segurança

- **Nunca commite segredos.** Credenciais vão exclusivamente por env vars da Vercel (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `OPR_SEED_*`, `GOOGLE_CLIENT_*`).
- `application-prod.properties` e afins estão no `.gitignore` — não devem ser versionados.

### Limitações

- **Dados:** o H2 em memória reseta a cada deploy/reinício. Use o perfil `prod` com PostgreSQL para persistir.
- **Sessões:** as sessões são em memória; em escala, autentique novamente se houver múltiplas instâncias.
- **Cold start:** a JVM foi otimizada para o limite da Vercel (heap 384m, SerialGC, C1-only, seed pós-boot assíncrono, pool de conexões lazy com timeout curto). Mesmo assim, o primeiro acesso após inatividade pode demorar alguns segundos — se retornar `INTERNAL_FUNCTION_INVOCATION_FAILED`/`500` no primeiro hit, aguarde o container esquentar e tente novamente.

## 📁 Estrutura do Projeto

```
opr-multas-app/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/opr/multas/
│       │   ├── config/       # Security, OAuth2, datasource (conversor p/ JDBC), seed, job de expiração, log de requisições
│       │   ├── controller/   # Auth, Multas, Revisão, Score, Admin, Health
│       │   ├── model/        # Usuario, Multa, AnexoMulta, VotoRevisao, HistoricoScore, enums
│       │   ├── repository/
│       │   └── service/      # Auth, Multas, Moderação, Score, OAuth2
│       └── resources/
│           ├── application.properties        # base (banco via env DATABASE_URL)
│           ├── application-dev.properties   # perfil dev (H2 console, cache Thymeleaf)
│           ├── static/css/app.css
│           └── templates/    # layout (navbar), login, multas, revisao, admin, usuario
├── Dockerfile.vercel   # build de container para a Vercel
├── .vercelignore
├── start-backend.ps1
└── README.md
```

## 📝 Licença

Este projeto é privado.
