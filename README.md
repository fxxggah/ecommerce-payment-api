# 💳 E-commerce Payment API

Serviço de pagamento do fluxo de e-commerce, desenvolvido como parte de um desafio prático da **DIO (Digital Innovation One)**.

Ele **não expõe endpoints de negócio**: funciona de forma orientada a eventos. Consome os checkouts criados no Kafka, registra o pagamento no PostgreSQL (com auditoria via Hibernate Envers) e publica um evento de pagamento aprovado, que o [serviço de checkout](https://github.com/fxxggah/ecommerce-checkout-api) consome para atualizar o status do pedido.

> O curso original usa o modelo antigo do Spring Cloud Stream (`@EnableBinding`, `@Input`, `@Output`, `@StreamListener`) com Avro. Este projeto foi **migrado para o modelo funcional** (um bean `Function`) e usa **JSON** nas mensagens, rodando em Spring Boot 3 e Java 17.

---

## 📌 Sumário

- [Tecnologias](#-tecnologias)
- [Arquitetura e fluxo](#-arquitetura-e-fluxo)
- [Pré-requisitos](#-pré-requisitos)
- [Como rodar](#-como-rodar)
- [Eventos Kafka](#-eventos-kafka)
- [Como testar](#-como-testar)
- [Configuração](#-configuração)
- [Estrutura do projeto](#-estrutura-do-projeto)
- [Decisões técnicas](#-decisões-técnicas)
- [Autor](#-autor)

---

## 🧰 Tecnologias

- **Java 17**
- **Spring Boot 3.3.7** (Web, Data JPA)
- **Spring Cloud Stream 4.x** (Spring Cloud 2023.0.3) com binder Kafka
- **Apache Kafka** (Confluent Platform) + Zookeeper
- **PostgreSQL**
- **Hibernate Envers** (auditoria das entidades)
- **Lombok**
- **Maven** (com Maven Wrapper)
- **Docker / Docker Compose**

---

## 🧭 Arquitetura e fluxo

```
Checkout API ──▶ Kafka: streaming.ecommerce.checkout.created
                                  │
                                  ▼
                         Payment API (este serviço)
                          ├─ grava o pagamento no PostgreSQL (payment)
                          └─▶ Kafka: streaming.ecommerce.payment.paid
                                          │
                                          ▼
                              Checkout API (status → APPROVED)
```

1. O checkout publica `CheckoutCreatedEvent` em `streaming.ecommerce.checkout.created`.
2. O bean `checkoutCreated` (uma `Function`) recebe o evento e grava um `Payment` com um código único (UUID) vinculado ao `checkoutCode`.
3. O mesmo bean devolve um `PaymentCreatedEvent`, que o Spring Cloud Stream publica em `streaming.ecommerce.payment.paid`.
4. O checkout consome esse evento e muda o pedido para `APPROVED`.

> Não há integração com gateway de pagamento real: o pagamento é sempre registrado e aprovado automaticamente. O foco do projeto é a comunicação assíncrona entre serviços.

---

## ✅ Pré-requisitos

- [JDK 17](https://adoptium.net/)
- [Docker](https://www.docker.com/) e Docker Compose
- Maven (opcional, o projeto inclui o `mvnw`)
- A infraestrutura (Kafka, Zookeeper e PostgreSQL) do projeto de checkout, descrita abaixo

---

## 🚀 Como rodar

### 1. Suba a infraestrutura

Este repositório não tem `docker-compose`. Use o do projeto de checkout, que sobe os dois bancos (`checkout` na porta `5432` e `payment` na porta `5433`), o Zookeeper e o Kafka:

```bash
git clone https://github.com/fxxggah/ecommerce-checkout-api.git
cd ecommerce-checkout-api/docker
docker compose up -d
```

Aguarde alguns segundos para o Kafka terminar de subir.

### 2. Clone e rode este projeto

```bash
git clone https://github.com/fxxggah/ecommerce-payment-api.git
cd ecommerce-payment-api
```

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run
```

O serviço sobe em **http://localhost:8082** e passa a escutar o tópico `streaming.ecommerce.checkout.created`.

> Para testar o fluxo completo, suba também o [checkout-api](https://github.com/fxxggah/ecommerce-checkout-api) (porta `8085`).

---

## 📨 Eventos Kafka

| Direção | Tópico                                 | Binding                    | Payload (JSON)                                           |
|---------|----------------------------------------|----------------------------|----------------------------------------------------------|
| Consome | `streaming.ecommerce.checkout.created` | `checkoutCreated-in-0`     | `{"checkoutCode":"...","status":"CREATED"}`              |
| Publica | `streaming.ecommerce.payment.paid`     | `checkoutCreated-out-0`    | `{"checkoutCode":"...","paymentCode":"..."}`             |

O consumidor usa o grupo `payment-api`.

---

## 🧪 Como testar

**1. Com o payment e o checkout rodando, crie um checkout:**

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8085/v1/checkout -ContentType "application/json" -Body '{"firstName":"Maria","lastName":"Silva","email":"maria@email.com","address":"Rua das Flores, 123","complement":"Apto 45","country":"Brasil","state":"SP","cep":"18600-000","saveAddress":true,"saveInfo":false,"paymentMethod":"credit_card","cardName":"MARIA SILVA","cardNumber":"0000000000000000","cardDate":"12/30","cardCvv":"000","products":["produto-1","produto-2"]}'
```

**2. Confira o pagamento criado** (o `checkout_code` deve bater com o `code` retornado pelo POST):

```bash
docker exec -it db-payment psql -U admin -d payment \
  -c "select code, checkout_code from payment order by id desc limit 1;"
```

**3. Confira o evento publicado:**

```bash
docker exec broker kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic streaming.ecommerce.payment.paid \
  --from-beginning
```

Saída esperada:

```json
{"checkoutCode":"...","paymentCode":"..."}
```

**4. Confira que o checkout foi aprovado:**

```bash
docker exec -it db-checkout psql -U admin -d checkout \
  -c "select code, status from checkout order by id desc limit 1;"
```

O status deve estar `APPROVED`.

**Testando só o payment (sem o checkout):** publique um evento na mão:

```bash
docker exec -it broker kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic streaming.ecommerce.checkout.created
```

Cole e dê Enter:

```json
{"checkoutCode":"codigo-de-teste-123","status":"CREATED"}
```

---

## ⚙️ Configuração

Principais propriedades em `src/main/resources/application.properties`:

| Propriedade                                | Valor padrão                                |
|--------------------------------------------|---------------------------------------------|
| `server.port`                              | `8082`                                      |
| `spring.application.name`                  | `payment-api`                               |
| `spring.datasource.url`                    | `jdbc:postgresql://localhost:5433/payment`  |
| `spring.datasource.username` / `password`  | `admin` / `admin`                           |
| `spring.jpa.hibernate.ddl-auto`            | `update`                                    |
| `spring.cloud.stream.kafka.binder.brokers` | `localhost:9092`                            |
| `spring.cloud.function.definition`         | `checkoutCreated`                           |

As credenciais acima são para **ambiente local de estudo**. Em qualquer ambiente real, use variáveis de ambiente ou um gerenciador de segredos.

---

## 🗂️ Estrutura do projeto

```
ecommerce-payment-api
├── src/main/java/dio/ecommerce/payment
│   ├── PaymentApplication.java
│   ├── entity/        # Payment (JPA + Envers)
│   ├── event/         # CheckoutCreatedEvent, PaymentCreatedEvent
│   ├── listener/      # CheckoutCreatedListener (Function: consome e publica)
│   ├── repository/    # PaymentRepository (Spring Data JPA)
│   └── service/       # PaymentService e implementação
├── src/main/resources/
│   └── application.properties
├── pom.xml
└── mvnw / mvnw.cmd
```

---

## 🧠 Decisões técnicas

- **Spring Cloud Stream funcional:** o modelo antigo foi removido no Spring Cloud Stream 4.x. Consumir e publicar agora cabe em um único bean `Function<CheckoutCreatedEvent, PaymentCreatedEvent>`, e o nome do bean define os bindings (`checkoutCreated-in-0` e `checkoutCreated-out-0`).
- **JSON no lugar de Avro:** elimina plugin Avro, dependências da Confluent e Schema Registry para este escopo. Os eventos são `record`s Java simples.
- **Binder `kafka` explícito:** o projeto também tem o `spring-cloud-stream-binder-kafka-streams` no classpath, então cada binding declara `binder=kafka` para evitar ambiguidade.
- **Versões alinhadas:** Spring Boot 3.3.x exige Spring Cloud `2023.0.x`. Usar uma release train mais nova (como `2025.1.x`, feita para o Boot 4) causa `ClassNotFoundException` na subida.
- **Auditoria com Envers:** a entidade `Payment` é anotada com `@Audited`, mantendo histórico de alterações.

---

## 👤 Autor

Desenvolvido por **Gabriel Oliveira** — [@fxxggah](https://github.com/fxxggah)

Projeto de estudo realizado no desafio prático da [DIO](https://www.dio.me/).
