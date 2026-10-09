CREATE TABLE IF NOT EXISTS managers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    role TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS employees (
    id INTEGER PRIMARY KEY,
    name TEXT, -- employee_name
    age INTEGER,
    department TEXT,
    job_role TEXT,
    monthly_income REAL,
    overtime TEXT,
    job_satisfaction INTEGER,
    years_at_company INTEGER
);

INSERT OR IGNORE INTO managers (username, password, role)
VALUES ('manager', 'admin123', 'MANAGER');

INSERT OR IGNORE INTO managers (username, password, role)
VALUES ('priya.lead', 'leadpass1', 'ENGINEERING_LEAD');