# Occupify — Agent Product & Domain Briefing

## 1. What is Occupify?

**Occupify** is a next-generation freelance career platform — described as **"LinkedIn tailored specifically for Freelancers"**. 

Unlike traditional job boards where freelancers are treated as secondary candidates, Occupify places **Freelancers as first-class citizens**:
- **Freelancer Career Hub**: Deep profile customization showcasing verified skills, portfolio case studies, hourly rates, and client ratings.
- **Intelligent Job Marketplace**: Fast, filterable job discovery matching freelance specialists with hiring clients and agencies.
- **Contract & Workspace Delivery**: Integrated workspace to track active projects, milestone deliverables, contract statuses, and collaboration.

## 2. Dual-Role User Model (EMPLOYEE & EMPLOYER là một người)

In the Occupify platform, **EMPLOYEE and EMPLOYER are the same person**:
- A single user account (`users.id`) accesses both workspaces on the same interface.
- One unified wallet (`wallets.user_id = users.id`) receives freelance earnings and pays client contract escrows.
- Users cannot contract with themselves (`contracts.employer_id <> contracts.employee_id`).
