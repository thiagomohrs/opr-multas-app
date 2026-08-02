# OPR (Overruled Peer Review) Multas App

Sistema full-stack em um único projeto **Spring Boot 3 (Java 21) + Thymeleaf**, com moderação de casos por reputação (OPR) e login via formulário ou **Google OAuth2**.

## 🚀 Tecnologias

- **Java 21**
- **Spring Boot 3.2.0** (Web MVC + Thymeleaf)
- **Spring Security** (form login + OAuth2 Client)
- **Spring Data JPA**
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

| Usuário  | Senha        | Role  | Score | Revisor |
|----------|--------------|-------|-------|---------|
| `admin`  | `admin123`   | ADMIN | 150   | Sim     |
| `revisor`| `revisor123` | USER  | 130   | Sim     |
| `user`   | `user123`    | USER  | 0     | Não     |

O `DataInitializer` também cria 3 casos de demonstração para popular a fila de revisão.

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

O seed de demonstração roda por padrão também em `prod` (para o primeiro acesso criar `admin`). Para desativar: `OPR_SEED_DEMO_DATA=false`. As senhas do seed vêm de env vars (`OPR_SEED_ADMIN_SENHA`, `OPR_SEED_USER_SENHA`, `OPR_SEED_REVISOR_SENHA`) — nunca hardcode em produção.

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
| `OPR_SEED_ADMIN_SENHA` | Não | Senha do usuário `admin` criado pelo seed (default `admin123`) |
| `OPR_SEED_USER_SENHA` | Não | Senha do usuário `user` criado pelo seed (default `user123`) |
| `OPR_SEED_REVISOR_SENHA` | Não | Senha dos revisores `revisor`, `revisor2`, `revisor3` (default `revisor123`) |
| `OPR_SEED_DEMO_DATA` | Não | `false` para desativar o seed de demonstração em produção |

\* Sem as credenciais Google, o login por formulário continua funcionando.

> **Importante:** ao usar Google OAuth2, adicione `https://<seu-projeto>.vercel.app/login/oauth2/code/google` como URI de redirecionamento no Google Cloud Console.

### Segurança

- **Nunca commite segredos.** Credenciais vão exclusivamente por env vars da Vercel (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `OPR_SEED_*`, `GOOGLE_CLIENT_*`).
- `application-prod.properties` e afins estão no `.gitignore` — não devem ser versionados.

### Limitações

- **Dados:** o H2 em memória reseta a cada deploy/reinício. Use o perfil `prod` com PostgreSQL para persistir.
- **Sessões:** as sessões são em memória; em escala, autentique novamente se houver múltiplas instâncias.
- **Cold start:** containers Java podem demorar alguns segundos para responder após inatividade.

## 📁 Estrutura do Projeto

```
opr-multas-app/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/opr/multas/
│       │   ├── config/       # Security, OAuth2, propriedades, job de expiração
│       │   ├── controller/   # Auth, Multas, Revisão, Score, Admin
│       │   ├── model/        # Usuario, Multa, VotoRevisao, HistoricoScore, enums
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
