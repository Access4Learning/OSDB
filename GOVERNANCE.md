# Project Governance

## Overview

The SIF Unity Broker is an open-source project owned by the **Maine Department of Education (MDOE)**. It was designed and built by [LevelData](https://leveldata.com) under contract. This document describes the project's governance model, decision-making process, and roles.

## Roles

### Project Owner — Maine Department of Education

MDOE owns the SIF Unity Broker and is responsible for its direction, maintenance, and operational decisions. MDOE determines the project roadmap, approves architectural changes, and manages the maintainer team.

### Technical Advisor — LevelData (Emeritus Creator)

LevelData designed and built the SIF Unity Broker's architecture, including the microservices structure, SIF 3.7 protocol implementation, security model, and message routing system.

As the original creator, LevelData serves as **Technical Advisor** on architectural matters. This means:

- **Architectural changes** (new services, protocol modifications, security model changes, database schema redesigns) should be discussed with LevelData before implementation to ensure alignment with the system's design principles and SIF specification compliance.
- **SIF specification interpretation** — LevelData has deep expertise in the SIF specifications and can provide guidance on correct protocol behavior.
- **Feature contributions** (bug fixes, new endpoints, UI improvements, documentation) can be submitted by anyone via pull request and do not require prior consultation.

As the initial development contract is complete, LevelData does not provide ongoing maintenance or support as part of this advisory role. In alignment with the MIT License, the software is provided 'as-is' without warranty of any kind. Consultation on architectural changes or custom feature development is available from LevelData under separate commercial engagement or future Statements of Work.

### Maintainers

Maintainers have write access to the repository and are responsible for reviewing pull requests, triaging issues, and managing releases. The maintainer team is managed by MDOE.

Current maintainers:

- MDOE Engineering Team
- [Additional maintainers to be added as the community grows]

Organizations deploying the broker in production are encouraged to nominate maintainers who can represent their operational needs.

### Contributors

Anyone can contribute to the project by submitting pull requests, reporting issues, or improving documentation. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Decision-Making

### Day-to-day decisions

Bug fixes, documentation improvements, and minor feature additions are decided by maintainers through the standard pull request review process.

### Architectural decisions

Changes that affect the system's core architecture require discussion before implementation. Examples include:

- Adding or removing microservices
- Changing the authentication or authorization model
- Modifying the SIF message processing pipeline
- Altering the database schema in ways that affect multiple services
- Changing the API gateway's filter chain

For these changes, open a GitHub Discussion or Issue describing the proposed change and tag it `architecture`. LevelData can provide technical guidance to ensure the change aligns with the system's design principles and SIF compliance requirements.

### SIF specification compliance

The SIF Unity Broker implements the SIF 3.7 specification. Changes that deviate from the specification require explicit justification and should be discussed with LevelData, who can advise on specification interpretation and compliance implications.

## Contact

- **Project Owner (MDOE):** [contact information to be added]
- **Technical Advisor (LevelData):** [contact information to be added]
- **GitHub Discussions:** [link to be added after public release]

## Changes to Governance

This governance model may evolve as the project's community grows. Changes to this document require approval from MDOE in consultation with LevelData.
