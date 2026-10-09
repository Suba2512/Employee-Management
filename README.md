# Enterprise Employee Database & Manager Portal

A full-featured JavaFX + SQLite enterprise desktop application for employee database management, department analytics, and personnel administration.

## ✨ Features & Enhancements

- **Modern Enterprise Dark UI**: Sleek, polished design with custom glassmorphism effects, status badges, and typography.
- **Full CRUD Operations**:
  - **Create**: Register new employees with ID auto-generation and full parameter validation.
  - **Read**: Explore employee records in responsive Card View or high-performance Table View.
  - **Update**: Edit details, salary raises, promotions, departments, and work metrics.
  - **Delete**: Safely delete personnel records with modal confirmation.
- **High-Performance Card View & Table View**:
  - **Card View**: Paginated (12 / 24 / 48 cards per page) with zero UI lag for 1,470+ records.
  - **Table View**: Fast virtualized JavaFX `TableView` with sortable columns and in-row action buttons.
- **Analytics & Visual Charts**:
  - **Department Distribution Pie Chart**: Real-time staff proportions across operating units.
  - **Department Average Salary Bar Chart**: Compensation comparison.
  - **Workforce Job Satisfaction Chart**: Breakdown of satisfaction levels (1 to 4).
  - **Overtime Workload Ratio**: Proportion of employees on standard vs overtime schedules.
- **Advanced Filtering & Instant Search**:
  - Search across Name, ID, Department, and Job Role simultaneously.
  - Department filter dropdown.
  - Overtime filter dropdown.
  - Sort by ID (Asc/Desc), Name (A-Z), Salary (High-Low / Low-High), and Experience.
- **CSV Data Export**: Export active/filtered records to CSV with standard file dialog.
- **Role-Based Access Control**:
  - **Portal Administrator** (`manager`): Unrestricted access to all departments and operations.
  - **Engineering / R&D Lead** (`priya.lead`): Scoped access to Research & Development / Engineering personnel (961 records).
- **Quick-Login Buttons**: Convenient 1-click login chips on the sign-in screen.

---

## 🚀 How to Run

### Option 1: 1-Click Launchers (Easiest)
Double-click `run.bat` or run:
```powershell
.\run.ps1
```

### Option 2: Maven CLI
```powershell
mvn javafx:run
```

---

## 👥 Demo Accounts

| Role | Username | Password | Access Scope |
| :--- | :--- | :--- | :--- |
| **Portal Administrator** | `manager` | `admin123` | Full enterprise access (all departments) |
| **Engineering / R&D Lead** | `priya.lead` | `leadpass1` | Scoped to Engineering & R&D team (961 staff) |

---

## 📁 System Architecture

- `src/main/java/ManagerPortal.java` - Complete JavaFX MVC application, SQLite connection, and charting engine.
- `src/main/resources/styles.css` - Custom CSS design system.
- `src/main/resources/database.sql` - Database schema and manager credentials.
- `src/main/resources/HR_Analytics_with_Employee_Names.csv` - Enterprise dataset (1,470 employees).
- `manager_portal.db` - Persistent SQLite database.
- `pom.xml` - Maven configuration with JavaFX 21 and SQLite JDBC dependencies.
- `run.bat` / `run.ps1` - Cross-environment executable launch scripts.
