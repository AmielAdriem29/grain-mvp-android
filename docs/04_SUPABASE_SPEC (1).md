# Supabase Database — Complete Setup Specification

This file contains everything needed to set up the database in isolation. You do not need to read any other document, and you do not write any application code. Your job produces exactly one output: a working connection string, handed to the backend developer.

---

## What You're Building

One PostgreSQL database, hosted on Supabase, with exactly one table. This is explicitly a temporary MVP setup — noted at the end of this file.

---

## Step 1 — Create the Project

1. Go to supabase.com and create a new project.
2. Choose a project name (anything reasonable, e.g. "grain-mvp").
3. Choose a strong database password when prompted. Save this somewhere safe — you'll need it for the connection string.
4. Choose a region close to wherever the backend server will actually run, if given the option.
5. Wait for the project to finish provisioning (usually a couple of minutes).

---

## Step 2 — Confirm the PostgreSQL Version

1. In the Supabase dashboard, find the database settings page (usually under Project Settings → Database).
2. Confirm the PostgreSQL version shown is 17 or 18. If it's something older, check if there's an option to upgrade, or flag this back to the team before proceeding — an outdated version isn't necessarily a blocker, but it's worth a heads up.

---

## Step 3 — Create the Table

1. In the Supabase dashboard, open the SQL Editor.
2. First, run this to make sure UUID generation works:
```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```
3. Then run this exact statement to create the table:
```sql
CREATE TABLE replicates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  technician_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sample_id TEXT NOT NULL,
  ai_predicted_grains JSONB NOT NULL,
  confirmed_grains JSONB NOT NULL,
  immature_weight NUMERIC(6,2) NOT NULL,
  percentage NUMERIC(5,2) NOT NULL,
  grade TEXT NOT NULL,
  original_image BYTEA,
  review_status TEXT NOT NULL DEFAULT 'unreviewed'
);
```
4. Go to the Table Editor in the Supabase dashboard and confirm you can see a table named `replicates` with these exact columns and zero rows.

Do not add, rename, or remove any columns. This schema is fixed and already agreed on by the rest of the team.

---

## Step 4 — Get the Connection String

1. In the Supabase dashboard, go to Project Settings → Database.
2. Find the "Connection string" section. Choose the connection string formatted for a direct connection (not the pooled/transaction-mode one, unless specifically told the backend needs pooling — for this MVP's expected traffic, a direct connection is simpler and sufficient).
3. It will look like this, with your actual password filled in:
```
postgresql://postgres:[YOUR-PASSWORD]@[YOUR-PROJECT-REF].supabase.co:5432/postgres
```
4. Replace `[YOUR-PASSWORD]` with the actual database password you set in Step 1.

---

## Step 5 — Confirm It Actually Works

Before handing this off, confirm the connection string works from outside Supabase's own dashboard. If you have Python available:

```python
import psycopg2
conn = psycopg2.connect("postgresql://postgres:[YOUR-PASSWORD]@[YOUR-PROJECT-REF].supabase.co:5432/postgres")
cur = conn.cursor()
cur.execute("SELECT 1;")
print(cur.fetchone())
conn.close()
```

If this prints `(1,)`, the connection works. If it fails, check:
- Is the password correct in the connection string?
- Does Supabase's dashboard show any network restriction settings that might need to be adjusted (check Project Settings → Database → Network Restrictions, and make sure it's not blocking outside connections by default)?

---

## Step 6 — Confirm Backups Are Enabled

1. In the Supabase dashboard, check Project Settings → Database → Backups (or similarly named section).
2. Confirm automatic daily backups are active on whatever plan tier is selected. Do not assume this — verify it directly in the dashboard.

---

## Step 7 — Hand Off

Give the backend developer exactly this one piece of information:

```
SUPABASE_DATABASE_URL = postgresql://postgres:[YOUR-PASSWORD]@[YOUR-PROJECT-REF].supabase.co:5432/postgres
```

Send this through a secure channel, not plain chat or email, since it grants full read/write access to the entire database.

---

## Important Note — This Is Temporary

This Supabase setup is explicitly marked as an MVP-only solution. It was chosen specifically because it requires almost no setup time, not because it's meant to be the permanent home for this data. Once the MVP is validated, whoever revisits this project should reconsider whether Supabase's hosting terms and free-tier limits are appropriate for continued or larger-scale use, or whether the database should move to a different hosting arrangement. You do not need to do anything about this now — just make sure this note doesn't get lost.

---

## Things You Do NOT Need To Do

- You do not write any backend or frontend code.
- You do not decide the schema. It's fixed, given to you above.
- You do not manage the API key used by the backend, Android app, or dashboard — that's a separate value the backend developer creates and shares, unrelated to your database password.
- You do not need to insert any test data unless you want to sanity-check the table yourself — the backend developer's own testing will populate real rows.
