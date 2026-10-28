# Occupify Payment - API Specification

> [!NOTE]
> **Base Port & Gateway Routing:**
> - Direct Service Port: `8183` (Internal)
> - Gateway Port: `8180` (External ingress entry point)
> - Gateway Routes: `/contracts/**`, `/wallets/**`, `/payments/**`
> - Database Port: PostgreSQL `5532` (`occupify_payment`), RabbitMQ `5672`
> - Status: *Architecture & API Contract Specification Phase*

---

## Standard Response Envelopes

Every API endpoint adheres to standardized Occupify response envelopes:

### Single Data Response (`SingleResponse<T>`)
```json
{
  "statusCode": 200,
  "message": "Operation completed successfully",
  "data": { ... }
}
```

### Resource Created Response (`CreatedResponse<T>`)
```json
{
  "statusCode": 201,
  "message": "Resource created successfully",
  "data": { ... }
}
```

### Paginated Data Response (`PageResponse<T>`)
```json
{
  "statusCode": 200,
  "message": "Operation completed successfully",
  "data": [ ... ],
  "paging": {
    "page": 0,
    "limit": 20,
    "total": 54,
    "totalPages": 3
  }
}
```

### Generic Action Success Response (`SuccessResponse`)
```json
{
  "statusCode": 200,
  "message": "Action completed successfully"
}
```

### Error Response (`ErrorResponse`)
```json
{
  "statusCode": 400,
  "errorCode": "PAY_004",
  "message": "Insufficient wallet balance to escrow milestone",
  "timestamp": "2026-10-18T22:30:00Z"
}
```

---

## 1. Domain Responsibilities

The **Payment Service** (`occupify-payment`) governs the financial, contractual, and escrow lifecycle of the platform:
1. **Contract Management (`/contracts/**`):** Contract drafting, digital agreement signing, and milestone schedules between employers and freelancers.
2. **Escrow Safeguards:** Holding employer funds in escrow upon contract activation and disbursing them upon verified deliverable approval.
3. **Wallet Management (`/wallets/**`):** User balance ledgers (available, pending, held in escrow), deposit credits, and bank withdrawals.
4. **Gateway Checkout & Webhooks (`/payments/**`):** Interfacing with payment gateways (e.g. VNPay, Stripe), handling asynchronous IPN/webhooks, and idempotency guarantees.

---

## 2. API Endpoints Specification

### 2.1 Contracts (`/contracts`)

#### 2.1.1 Initiate Draft Contract
- **Endpoint:** `POST /contracts`
- **Description:** Creates a new contract draft between employer and freelancer from an accepted bid.
- **Authorization:** Authenticated (`X-User-Role: USER` / Employer)
- **Headers:** `Content-Type: application/json`, `X-User-Id: <uuid>`

##### Request Body
| Field | Type | Required | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `projectId` | UUID | Yes | Valid project UUID | Associated project |
| `freelancerId`| UUID | Yes | Valid user UUID | Freelancer party |
| `title` | String | Yes | Max 255 chars, not blank | Contract title |
| `description`| String | No | Max 4000 chars | Terms and scope |
| `totalAmount`| BigDecimal | Yes | $> 0$ | Total contract value |
| `currency` | String | Yes | 3-letter ISO code (`USD`, `VND`) | Currency code |
| `milestones` | Array[Object] | Yes | At least 1 milestone | Schedule of deliverables |
| `milestones[].title` | String | Yes | Max 200 chars | Milestone name |
| `milestones[].amount` | BigDecimal | Yes | $> 0$, sum must equal `totalAmount` | Milestone escrow amount |
| `milestones[].dueDate` | String | No | Format `YYYY-MM-DD` | Target delivery date |

##### Request Body Example
```json
{
  "projectId": "11111111-2222-3333-4444-555555555555",
  "freelancerId": "22222222-3333-4444-5555-666666666666",
  "title": "Backend Microservice Architecture & RabbitMQ Pipeline",
  "description": "Full delivery of high-throughput notification and event streaming microservices.",
  "totalAmount": 1500.00,
  "currency": "USD",
  "milestones": [
    {
      "title": "Milestone 1: Service scaffolding and database migrations",
      "amount": 500.00,
      "dueDate": "2026-11-01"
    },
    {
      "title": "Milestone 2: RabbitMQ integration, retry, and circuit breaker",
      "amount": 1000.00,
      "dueDate": "2026-11-20"
    }
  ]
}
```

##### Response Example (`201 Created` - `CreatedResponse<ContractResponse>`)
```json
{
  "statusCode": 201,
  "message": "Contract created successfully in DRAFT status",
  "data": {
    "id": "77777777-8888-9999-aaaa-bbbbbbbbbbbb",
    "projectId": "11111111-2222-3333-4444-555555555555",
    "employerId": "33333333-4444-5555-6666-777777777777",
    "freelancerId": "22222222-3333-4444-5555-666666666666",
    "title": "Backend Microservice Architecture & RabbitMQ Pipeline",
    "description": "Full delivery of high-throughput notification and event streaming microservices.",
    "totalAmount": 1500.00,
    "currency": "USD",
    "status": "DRAFT",
    "employerSigned": true,
    "freelancerSigned": false,
    "escrowStatus": "UNPAID",
    "milestones": [
      {
        "id": "88888888-9999-aaaa-bbbb-cccccccccccc",
        "title": "Milestone 1: Service scaffolding and database migrations",
        "amount": 500.00,
        "status": "PENDING",
        "dueDate": "2026-11-01"
      },
      {
        "id": "99999999-aaaa-bbbb-cccc-dddddddddddd",
        "title": "Milestone 2: RabbitMQ integration, retry, and circuit breaker",
        "amount": 1000.00,
        "status": "PENDING",
        "dueDate": "2026-11-20"
      }
    ],
    "createdAt": "2026-10-18T22:30:00Z",
    "updatedAt": "2026-10-18T22:30:00Z"
  }
}
```

---

#### 2.1.2 Get Contract by ID
- **Endpoint:** `GET /contracts/{id}`
- **Description:** Retrieves details, signing status, and milestone progression of a contract.
- **Authorization:** Authenticated (Contract party: Employer or Freelancer, or Admin)

##### Response Example (`200 OK` - `SingleResponse<ContractResponse>`)
```json
{
  "statusCode": 200,
  "message": "Operation completed successfully",
  "data": {
    "id": "77777777-8888-9999-aaaa-bbbbbbbbbbbb",
    "projectId": "11111111-2222-3333-4444-555555555555",
    "employerId": "33333333-4444-5555-6666-777777777777",
    "freelancerId": "22222222-3333-4444-5555-666666666666",
    "title": "Backend Microservice Architecture & RabbitMQ Pipeline",
    "totalAmount": 1500.00,
    "currency": "USD",
    "status": "ACTIVE",
    "employerSigned": true,
    "freelancerSigned": true,
    "escrowStatus": "FUNDED",
    "milestones": [
      {
        "id": "88888888-9999-aaaa-bbbb-cccccccccccc",
        "title": "Milestone 1: Service scaffolding and database migrations",
        "amount": 500.00,
        "status": "COMPLETED",
        "dueDate": "2026-11-01"
      },
      {
        "id": "99999999-aaaa-bbbb-cccc-dddddddddddd",
        "title": "Milestone 2: RabbitMQ integration, retry, and circuit breaker",
        "amount": 1000.00,
        "status": "IN_PROGRESS",
        "dueDate": "2026-11-20"
      }
    ],
    "createdAt": "2026-10-18T22:30:00Z",
    "updatedAt": "2026-10-19T08:15:00Z"
  }
}
```

---

#### 2.1.3 Sign Contract
- **Endpoint:** `PATCH /contracts/{id}/sign`
- **Description:** Submits digital signature or acceptance of contract terms. Transitions contract to `ACTIVE` when both parties have signed.
- **Authorization:** Authenticated (Contract party)
- **Headers:** `Content-Type: application/json`, `X-User-Id: <uuid>`

##### Request Body
| Field | Type | Required | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `acceptedTerms` | Boolean | Yes | Must be `true` | Acceptance confirmation |
| `signatureNotes`| String | No | Max 500 chars | Optional signature acknowledgment |

##### Request Body Example
```json
{
  "acceptedTerms": true,
  "signatureNotes": "Terms acknowledged and accepted by freelancer."
}
```

##### Response Example (`200 OK` - `SingleResponse<ContractResponse>`)
```json
{
  "statusCode": 200,
  "message": "Contract terms successfully signed",
  "data": {
    "id": "77777777-8888-9999-aaaa-bbbbbbbbbbbb",
    "status": "ACTIVE",
    "employerSigned": true,
    "freelancerSigned": true,
    "escrowStatus": "FUNDED",
    "updatedAt": "2026-10-19T08:15:00Z"
  }
}
```

---

#### 2.1.4 Release Milestone Escrow
- **Endpoint:** `POST /contracts/{id}/milestones/{milestoneId}/release`
- **Description:** Releases escrowed funds for an approved milestone to the freelancer's wallet.
- **Authorization:** Authenticated (Employer only)
- **Headers:** `Content-Type: application/json`, `X-User-Id: <uuid>`

##### Request Body
| Field | Type | Required | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `approvalNotes` | String | No | Max 1000 chars | Deliverable inspection feedback |

##### Request Body Example
```json
{
  "approvalNotes": "Milestone deliverables inspected and verified against project requirements."
}
```

##### Response Example (`200 OK` - `SingleResponse<MilestoneReleaseResponse>`)
```json
{
  "statusCode": 200,
  "message": "Milestone escrow funds successfully released",
  "data": {
    "contractId": "77777777-8888-9999-aaaa-bbbbbbbbbbbb",
    "milestoneId": "88888888-9999-aaaa-bbbb-cccccccccccc",
    "releasedAmount": 500.00,
    "currency": "USD",
    "milestoneStatus": "RELEASED",
    "recipientFreelancerId": "22222222-3333-4444-5555-666666666666",
    "releasedAt": "2026-10-19T10:00:00Z"
  }
}
```

---

### 2.2 Wallets (`/wallets`)

#### 2.2.1 Get Current User Wallet
- **Endpoint:** `GET /wallets/me`
- **Description:** Retrieves the authenticated user's wallet balances.
- **Authorization:** Authenticated (`X-User-Id`)

##### Response Example (`200 OK` - `SingleResponse<WalletResponse>`)
```json
{
  "statusCode": 200,
  "message": "Operation completed successfully",
  "data": {
    "walletId": "33333333-7777-8888-9999-000000000000",
    "userId": "22222222-3333-4444-5555-666666666666",
    "availableBalance": 2450.00,
    "escrowLockedBalance": 1000.00,
    "pendingWithdrawalBalance": 0.00,
    "currency": "USD",
    "updatedAt": "2026-10-19T10:00:00Z"
  }
}
```

---

#### 2.2.2 Get Wallet Transaction History
- **Endpoint:** `GET /wallets/me/transactions`
- **Description:** Retrieves paginated ledger transactions for the authenticated user.
- **Authorization:** Authenticated (`X-User-Id`)
- **Query Parameters:**
  - `page`: Integer (default: `0`)
  - `limit`: Integer (default: `20`)
  - `type`: Enum filter (`CREDIT`, `DEBIT`, `ESCROW_HOLD`, `ESCROW_RELEASE`, `WITHDRAWAL`)

##### Response Example (`200 OK` - `PageResponse<WalletTransactionResponse>`)
```json
{
  "statusCode": 200,
  "message": "Operation completed successfully",
  "data": [
    {
      "id": "bbbbbbbb-1111-2222-3333-444444444444",
      "type": "ESCROW_RELEASE",
      "amount": 500.00,
      "fee": 25.00,
      "netAmount": 475.00,
      "currency": "USD",
      "status": "COMPLETED",
      "referenceType": "MILESTONE",
      "referenceId": "88888888-9999-aaaa-bbbb-cccccccccccc",
      "description": "Milestone escrow payout: Milestone 1",
      "createdAt": "2026-10-19T10:00:00Z"
    }
  ],
  "paging": {
    "page": 0,
    "limit": 20,
    "total": 1,
    "totalPages": 1
  }
}
```

---

#### 2.2.3 Request Balance Withdrawal
- **Endpoint:** `POST /wallets/me/withdraw`
- **Description:** Requests a payout from available balance to a linked bank account.
- **Authorization:** Authenticated (`X-User-Id`)
- **Headers:** `Content-Type: application/json`

##### Request Body
| Field | Type | Required | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `amount` | BigDecimal | Yes | $> 10.00$, $\le$ available balance | Withdrawal amount |
| `bankCode` | String | Yes | Valid bank code (e.g. `VCB`, `TCB`, `MB`) | Beneficiary bank |
| `bankAccountNumber`| String | Yes | 6-24 alphanumeric chars | Account number |
| `accountHolderName` | String | Yes | Max 100 chars, uppercase alphabet | Name on bank account |

##### Request Body Example
```json
{
  "amount": 350.00,
  "bankCode": "VCB",
  "bankAccountNumber": "0071001234567",
  "accountHolderName": "NGUYEN VAN A"
}
```

##### Response Example (`201 Created` - `CreatedResponse<WithdrawalResponse>`)
```json
{
  "statusCode": 201,
  "message": "Withdrawal request submitted successfully",
  "data": {
    "id": "dddddddd-3333-4444-5555-666666666666",
    "walletId": "33333333-7777-8888-9999-000000000000",
    "amount": 350.00,
    "fee": 1.50,
    "netAmount": 348.50,
    "currency": "USD",
    "bankCode": "VCB",
    "maskedAccountNumber": "******4567",
    "status": "PENDING",
    "requestedAt": "2026-10-19T10:30:00Z"
  }
}
```

---

### 2.3 Payments & Top-up (`/payments`)

#### 2.3.1 Create Payment Checkout Session
- **Endpoint:** `POST /payments/checkout`
- **Description:** Initiates a payment session with an external payment provider (VNPay, Stripe) to top up wallet or fund contract.
- **Authorization:** Authenticated (`X-User-Id`)
- **Headers:** `Content-Type: application/json`

##### Request Body
| Field | Type | Required | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `amount` | BigDecimal | Yes | Min 1.00 | Deposit amount |
| `currency` | String | Yes | `USD` or `VND` | Currency code |
| `paymentProvider`| String | Yes | `VNPAY`, `STRIPE` | Gateway provider |
| `returnUrl` | String | Yes | Valid HTTPS URL | Redirect URL on user completion |
| `cancelUrl` | String | Yes | Valid HTTPS URL | Redirect URL on cancellation |

##### Request Body Example
```json
{
  "amount": 500.00,
  "currency": "USD",
  "paymentProvider": "STRIPE",
  "returnUrl": "https://occupify.app/payments/success",
  "cancelUrl": "https://occupify.app/payments/cancel"
}
```

##### Response Example (`201 Created` - `CreatedResponse<PaymentCheckoutResponse>`)
```json
{
  "statusCode": 201,
  "message": "Payment checkout session initialized",
  "data": {
    "paymentId": "ffffffff-5555-6666-7777-888888888888",
    "providerSessionId": "cs_test_a1b2c3d4e5f6g7h8",
    "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_a1b2c3d4e5f6g7h8",
    "amount": 500.00,
    "currency": "USD",
    "status": "PENDING",
    "expiresAt": "2026-10-19T11:30:00Z"
  }
}
```

---

#### 2.3.2 Payment Gateway Webhook
- **Endpoint:** `POST /payments/webhook`
- **Description:** Asynchronous callback endpoint invoked by external payment provider with HMAC cryptographic signature verification.
- **Authorization:** Public (HMAC Verified)
- **Headers:** `Content-Type: application/json`, `X-Signature: <hmac-signature>`

##### Request Body Example
```json
{
  "eventId": "evt_3MtwBwLkdIwHu7ix28a3tqPa",
  "provider": "STRIPE",
  "transactionId": "txn_3MtwBwLkdIwHu7ix28a3tqPa",
  "paymentId": "ffffffff-5555-6666-7777-888888888888",
  "status": "SUCCEEDED",
  "amountPaid": 500.00,
  "currency": "USD",
  "payloadSignature": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```

##### Response Example (`200 OK` - `SuccessResponse`)
```json
{
  "statusCode": 200,
  "message": "Webhook processed successfully"
}
```

---

## 3. RabbitMQ Asynchronous Events

The Payment Service publishes and consumes messages across the `occupify.exchange` topic exchange:

| Event Name | Routing Key | Purpose |
| :--- | :--- | :--- |
| `ContractFundedEvent` | `payment.contract.funded` | Published when contract escrow is fully deposited. |
| `MilestoneReleasedEvent` | `payment.milestone.released` | Published when escrow is disbursed to freelancer wallet. |
| `WithdrawalCompletedEvent` | `payment.withdrawal.completed` | Published when bank transfer clears. |

### Payload Contract (`MilestoneReleasedEvent`)
```json
{
  "contractId": "77777777-8888-9999-aaaa-bbbbbbbbbbbb",
  "milestoneId": "88888888-9999-aaaa-bbbb-cccccccccccc",
  "employerId": "33333333-4444-5555-6666-777777777777",
  "freelancerId": "22222222-3333-4444-5555-666666666666",
  "amount": 500.00,
  "currency": "USD",
  "releasedAt": "2026-10-19T10:00:00Z"
}
```
