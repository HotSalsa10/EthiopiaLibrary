# Project Overview: Charity Library Management System (Ethiopia)
**Target Audience:** Library staff and volunteers.
**Hardware Constraint:** Must run smoothly on cheap, low-end Android tablets.
**Language Requirements:** The UI must support three languages: Amharic (Ethiopian) as the default/main language, Arabic (requires Right-to-Left support), and English.

## Environment & Connectivity (The "Offline-First" Rule)
*   **Intermittent Internet:** The library does not have stable internet. The application must be strictly "Offline-First". 
*   **Local Source of Truth:** All data (books, members, transactions) must be saved to a local, on-device database instantly. The UI must read only from this local storage, never waiting for a network request.
*   **Background Syncing:** The app should detect when the tablet connects to the internet and automatically run a background process to sync/backup the local data to a cloud database. 

## Architectural & Tech Stack Freedom
*   **Tech Stack:** You (Claude) are the lead architect. Choose the best, most lightweight tech stack and frameworks to build this Android application. Prioritize performance, battery efficiency, and ease of maintenance.
*   **Crash Resilience:** Because this will run on cheap hardware that may lose power, ensure the local database is highly resistant to corruption (e.g., using Write-Ahead Logging if selecting SQLite).
*   **UI/UX:** The interface must be modern, highly intuitive, and optimized for a tablet screen with large, accessible touch targets.

## AI SDLC & Coding Guidelines (How to Assist Me)
*   **Plan Before Coding:** Before writing any application code, evaluate and propose the tech stack, the database schema, and the sync strategy. Wait for my approval.
*   **EPIC Sequence:** Always follow the Explore, Plan, Implement, and Commit sequence. Do not jump straight into massive code generation.
*   **Pacing:** Implement one architectural layer at a time (e.g., build and verify the local database completely before touching the UI). Wait for my confirmation between steps.
*   **Address Root Causes:** If I paste a compilation error, do not just suppress it. Explain the root mechanical cause of the error first, then provide the fix.