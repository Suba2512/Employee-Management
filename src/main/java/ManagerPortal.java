import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;

public class ManagerPortal extends Application {

    private static final String DB_URL = "jdbc:sqlite:manager_portal.db";
    private final DecimalFormat money = new DecimalFormat("#,##0");

    private Connection connection;
    private Stage primaryStage;

    private String currentUsername;
    private String currentRole;

    // View State
    private enum ViewMode { CARDS, TABLE, ANALYTICS }
    private ViewMode activeView = ViewMode.CARDS;

    // Filter & Search Controls
    private TextField searchField;
    private ComboBox<String> departmentFilter;
    private ComboBox<String> overtimeFilter;
    private ComboBox<String> sortOrderFilter;

    // Pagination State for Card View
    private int currentPage = 1;
    private int pageSize = 12;
    private int totalFilteredRecords = 0;
    private Label pageInfoLabel;
    private Button prevPageBtn;
    private Button nextPageBtn;

    // UI Containers
    private StackPane mainViewContainer;
    private FlowPane cardFlowPane;
    private TableView<Employee> employeeTableView;
    private VBox analyticsContainer;

    // KPI Labels
    private Label totalEmployeesKpi;
    private Label totalDepartmentsKpi;
    private Label overtimeKpi;
    private Label avgIncomeKpi;
    private Label totalPayrollKpi;

    // Navigation toggle buttons
    private Button cardsNavBtn;
    private Button tableNavBtn;
    private Button analyticsNavBtn;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        try {
            connection = DriverManager.getConnection(DB_URL);
            initializeDatabase();
        } catch (Exception e) {
            showFatalError("Database initialization error: " + e.getMessage());
            return;
        }

        stage.setTitle("Employee Database - Enterprise Manager Portal");
        showLogin(stage);
    }

    // =========================================================
    // DATABASE INITIALIZATION
    // =========================================================

    private void initializeDatabase() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }

        InputStream in = getClass().getResourceAsStream("/database.sql");
        if (in != null) {
            StringBuilder sql = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().startsWith("--")) {
                        sql.append(line).append('\n');
                    }
                }
            }
            for (String statement : sql.toString().split(";")) {
                String s = statement.trim();
                if (!s.isEmpty()) {
                    try (Statement st = connection.createStatement()) {
                        st.execute(s);
                    }
                }
            }
        }

        importEmployeesFromCSV();
    }

    private void importEmployeesFromCSV() throws Exception {
        String checkSql = "SELECT COUNT(*) FROM employees";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        InputStream in = getClass().getResourceAsStream("/HR_Analytics_with_Employee_Names.csv");
        if (in == null) {
            return;
        }

        String sql = """
                INSERT INTO employees
                (id, name, age, department, job_role, monthly_income, overtime, job_satisfaction, years_at_company)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             PreparedStatement ps = connection.prepareStatement(sql)) {

            br.readLine(); // Skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = parseCSVLine(line);
                if (data.length < 35) continue;

                int age = parseIntSafe(data[0], 30);
                String employeeName = data[1].trim();
                int employeeNumber = parseIntSafe(data[10], 0);
                String department = data[5].trim();
                String jobRole = data[16].trim();
                double monthlyIncome = parseDoubleSafe(data[19], 5000);
                String overtime = data[23].trim();
                int jobSatisfaction = parseIntSafe(data[17], 3);
                int yearsAtCompany = parseIntSafe(data[32], 2);

                ps.setInt(1, employeeNumber);
                ps.setString(2, employeeName);
                ps.setInt(3, age);
                ps.setString(4, department);
                ps.setString(5, jobRole);
                ps.setDouble(6, monthlyIncome);
                ps.setString(7, overtime);
                ps.setInt(8, jobSatisfaction);
                ps.setInt(9, yearsAtCompany);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String[] parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        boolean insideQuotes = false;
        StringBuilder value = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                insideQuotes = !insideQuotes;
            } else if (c == ',' && !insideQuotes) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }
        values.add(value.toString().trim());
        return values.toArray(new String[0]);
    }

    private int parseIntSafe(String val, int fallback) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parseDoubleSafe(String val, double fallback) {
        try {
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    // =========================================================
    // AUTHENTICATION & LOGIN SCREEN
    // =========================================================

    private void showLogin(Stage stage) {
        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(420);

        Label title = new Label("Manager Portal");
        title.getStyleClass().add("title");

        Label subtitle = new Label("Sign in to access corporate employee records & analytics");
        subtitle.getStyleClass().add("subtitle");

        TextField username = new TextField();
        username.setPromptText("Username");
        username.getStyleClass().add("field");

        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.getStyleClass().add("field");

        Label error = new Label();
        error.getStyleClass().add("error");

        Button login = new Button("Sign In");
        login.getStyleClass().add("primary-button");
        login.setMaxWidth(Double.MAX_VALUE);

        // Quick demo login chips
        Label demoHeader = new Label("Quick Access Accounts:");
        demoHeader.getStyleClass().add("info");

        HBox demoChips = new HBox(8);
        Button managerChip = new Button("Admin: manager");
        managerChip.getStyleClass().add("chip-button");
        managerChip.setOnAction(e -> {
            username.setText("manager");
            password.setText("admin123");
            login.fire();
        });

        Button leadChip = new Button("Lead: priya.lead");
        leadChip.getStyleClass().add("chip-button");
        leadChip.setOnAction(e -> {
            username.setText("priya.lead");
            password.setText("leadpass1");
            login.fire();
        });
        demoChips.getChildren().addAll(managerChip, leadChip);

        login.setOnAction(e -> {
            try {
                String role = authenticate(username.getText().trim(), password.getText());
                if (role == null) {
                    error.setText("Invalid username or password.");
                } else {
                    currentUsername = username.getText().trim();
                    currentRole = role;
                    showDashboard(stage);
                }
            } catch (SQLException ex) {
                error.setText("Login error: " + ex.getMessage());
            }
        });

        password.setOnAction(e -> login.fire());

        card.getChildren().addAll(
                title, subtitle, new Separator(),
                username, password, login, error,
                demoHeader, demoChips
        );

        StackPane root = new StackPane(card);
        root.getStyleClass().add("login-page");
        root.setPadding(new Insets(30));

        Scene scene = new Scene(root, 920, 640);
        addCss(scene);
        stage.setScene(scene);
        stage.show();
    }

    private String authenticate(String username, String password) throws SQLException {
        String sql = "SELECT role FROM managers WHERE username = ? AND password = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("role") : null;
            }
        }
    }

    // =========================================================
    // DASHBOARD & NAVIGATION
    // =========================================================

    private void showDashboard(Stage stage) {
        BorderPane root = new BorderPane();

        // Header
        HBox header = buildHeader(stage);
        root.setTop(header);

        // Main Body VBox
        VBox body = new VBox(16);
        body.setPadding(new Insets(20, 26, 20, 26));

        // KPI Summary Cards
        HBox kpiBox = buildKpiSection();

        // Search & Filtering Bar
        HBox filterBar = buildFilterBar();

        // Main View Container (Holds Cards, Table, or Analytics)
        mainViewContainer = new StackPane();
        VBox.setVgrow(mainViewContainer, Priority.ALWAYS);

        // 1. Card View with Pagination
        VBox cardViewWrapper = buildCardViewWrapper();

        // 2. Table View
        VBox tableViewWrapper = buildTableViewWrapper();

        // 3. Analytics View
        analyticsContainer = buildAnalyticsView();

        mainViewContainer.getChildren().addAll(cardViewWrapper, tableViewWrapper, analyticsContainer);

        body.getChildren().addAll(kpiBox, filterBar, mainViewContainer);
        root.setCenter(body);

        // Switch to default view
        switchView(ViewMode.CARDS);

        // Initial Data Load
        refreshDashboardData();

        Scene scene = new Scene(root, 1200, 780);
        addCss(scene);
        stage.setScene(scene);
        stage.show();
    }

    private HBox buildHeader(Stage stage) {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");

        Label brand = new Label("Employee Database Portal");
        brand.getStyleClass().add("title");

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        // Nav toggle buttons
        cardsNavBtn = new Button("Cards View");
        cardsNavBtn.getStyleClass().add("nav-toggle-button");
        cardsNavBtn.setOnAction(e -> switchView(ViewMode.CARDS));

        tableNavBtn = new Button("Table View");
        tableNavBtn.getStyleClass().add("nav-toggle-button");
        tableNavBtn.setOnAction(e -> switchView(ViewMode.TABLE));

        analyticsNavBtn = new Button("Insights & Charts");
        analyticsNavBtn.getStyleClass().add("nav-toggle-button");
        analyticsNavBtn.setOnAction(e -> switchView(ViewMode.ANALYTICS));

        HBox navGroup = new HBox(8, cardsNavBtn, tableNavBtn, analyticsNavBtn);
        navGroup.setAlignment(Pos.CENTER);

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Label userLabel = new Label("User: " + currentUsername);
        userLabel.setStyle("-fx-text-fill: #f1f5f9; -fx-font-weight: 600;");

        String displayRole = "ENGINEERING_LEAD".equals(currentRole) ? "Engineering / R&D Lead" : "Portal Administrator";
        Label roleBadge = new Label(displayRole);
        roleBadge.getStyleClass().add("role-badge");

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("secondary-button");
        logoutBtn.setOnAction(e -> showLogin(stage));

        header.getChildren().addAll(brand, spacer1, navGroup, spacer2, userLabel, roleBadge, logoutBtn);
        return header;
    }

    private void switchView(ViewMode mode) {
        this.activeView = mode;
        cardsNavBtn.getStyleClass().remove("nav-toggle-button-active");
        tableNavBtn.getStyleClass().remove("nav-toggle-button-active");
        analyticsNavBtn.getStyleClass().remove("nav-toggle-button-active");

        mainViewContainer.getChildren().forEach(n -> n.setVisible(false));

        switch (mode) {
            case CARDS -> {
                cardsNavBtn.getStyleClass().add("nav-toggle-button-active");
                mainViewContainer.getChildren().get(0).setVisible(true);
            }
            case TABLE -> {
                tableNavBtn.getStyleClass().add("nav-toggle-button-active");
                mainViewContainer.getChildren().get(1).setVisible(true);
            }
            case ANALYTICS -> {
                analyticsNavBtn.getStyleClass().add("nav-toggle-button-active");
                mainViewContainer.getChildren().get(2).setVisible(true);
                refreshAnalytics();
            }
        }
    }

    // =========================================================
    // KPI SECTION
    // =========================================================

    private HBox buildKpiSection() {
        HBox kpiBox = new HBox(14);

        totalEmployeesKpi = new Label("0");
        totalDepartmentsKpi = new Label("0");
        overtimeKpi = new Label("0");
        avgIncomeKpi = new Label("₹0");
        totalPayrollKpi = new Label("₹0");

        kpiBox.getChildren().addAll(
                createKpiCard("Total Employees", totalEmployeesKpi, "Active personnel records"),
                createKpiCard("Departments", totalDepartmentsKpi, "Operating business units"),
                createKpiCard("Overtime Personnel", overtimeKpi, "Staff with overtime scheduled"),
                createKpiCard("Average Salary", avgIncomeKpi, "Monthly compensation avg"),
                createKpiCard("Total Monthly Payroll", totalPayrollKpi, "Cumulative workforce payroll")
        );
        return kpiBox;
    }

    private VBox createKpiCard(String label, Label valueLabel, String subtext) {
        VBox card = new VBox(6);
        card.getStyleClass().add("kpi-card");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("kpi-label");

        valueLabel.getStyleClass().add("kpi-value");

        Label sub = new Label(subtext);
        sub.getStyleClass().add("kpi-subtext");

        card.getChildren().addAll(lbl, valueLabel, sub);
        return card;
    }

    // =========================================================
    // FILTER BAR & ACTIONS
    // =========================================================

    private HBox buildFilterBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍 Search by ID, name, role or dept...");
        searchField.getStyleClass().add("field");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, old, val) -> {
            currentPage = 1;
            refreshDashboardData();
        });

        departmentFilter = new ComboBox<>();
        departmentFilter.getStyleClass().add("combo-box");
        departmentFilter.getItems().addAll("All Departments", "Research & Development", "Sales", "Human Resources");
        departmentFilter.setValue("All Departments");
        departmentFilter.setOnAction(e -> {
            currentPage = 1;
            refreshDashboardData();
        });

        overtimeFilter = new ComboBox<>();
        overtimeFilter.getStyleClass().add("combo-box");
        overtimeFilter.getItems().addAll("All Overtime", "Overtime: Yes", "Overtime: No");
        overtimeFilter.setValue("All Overtime");
        overtimeFilter.setOnAction(e -> {
            currentPage = 1;
            refreshDashboardData();
        });

        sortOrderFilter = new ComboBox<>();
        sortOrderFilter.getStyleClass().add("combo-box");
        sortOrderFilter.getItems().addAll(
                "Sort: ID (Asc)",
                "Sort: ID (Desc)",
                "Sort: Name (A-Z)",
                "Sort: Salary (High - Low)",
                "Sort: Salary (Low - High)",
                "Sort: Experience (Years)"
        );
        sortOrderFilter.setValue("Sort: ID (Asc)");
        sortOrderFilter.setOnAction(e -> refreshDashboardData());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ Add Employee");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> showAddEmployeeDialog());

        Button exportBtn = new Button("Export CSV");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setOnAction(e -> exportToCsv());

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("secondary-button");
        resetBtn.setOnAction(e -> {
            searchField.clear();
            departmentFilter.setValue("All Departments");
            overtimeFilter.setValue("All Overtime");
            sortOrderFilter.setValue("Sort: ID (Asc)");
            currentPage = 1;
            refreshDashboardData();
        });

        bar.getChildren().addAll(searchField, departmentFilter, overtimeFilter, sortOrderFilter, spacer, addBtn, exportBtn, resetBtn);
        return bar;
    }

    // =========================================================
    // CARD VIEW WITH PAGINATION
    // =========================================================

    private VBox buildCardViewWrapper() {
        VBox wrapper = new VBox(12);
        VBox.setVgrow(wrapper, Priority.ALWAYS);

        cardFlowPane = new FlowPane(14, 14);
        cardFlowPane.setPadding(new Insets(4));
        cardFlowPane.setAlignment(Pos.TOP_LEFT);

        ScrollPane scroll = new ScrollPane(cardFlowPane);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Pagination Bar
        HBox pageBar = new HBox(12);
        pageBar.setAlignment(Pos.CENTER);
        pageBar.setPadding(new Insets(10, 0, 0, 0));

        prevPageBtn = new Button("◀ Previous");
        prevPageBtn.getStyleClass().add("secondary-button");
        prevPageBtn.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                loadCards();
            }
        });

        pageInfoLabel = new Label("Page 1 of 1");
        pageInfoLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700;");

        nextPageBtn = new Button("Next ▶");
        nextPageBtn.getStyleClass().add("secondary-button");
        nextPageBtn.setOnAction(e -> {
            int maxPage = (int) Math.ceil((double) totalFilteredRecords / pageSize);
            if (currentPage < maxPage) {
                currentPage++;
                loadCards();
            }
        });

        ComboBox<Integer> pageSizeCombo = new ComboBox<>();
        pageSizeCombo.getStyleClass().add("combo-box");
        pageSizeCombo.getItems().addAll(12, 24, 48);
        pageSizeCombo.setValue(pageSize);
        pageSizeCombo.setOnAction(e -> {
            pageSize = pageSizeCombo.getValue();
            currentPage = 1;
            loadCards();
        });

        Label sizeLabel = new Label("Per Page:");
        sizeLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        pageBar.getChildren().addAll(prevPageBtn, pageInfoLabel, nextPageBtn, new Separator(), sizeLabel, pageSizeCombo);

        wrapper.getChildren().addAll(scroll, pageBar);
        return wrapper;
    }

    private void loadCards() {
        cardFlowPane.getChildren().clear();

        List<Employee> list = queryEmployees(true);
        for (Employee emp : list) {
            cardFlowPane.getChildren().add(buildEmployeeCard(emp));
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) totalFilteredRecords / pageSize));
        pageInfoLabel.setText(String.format("Page %d of %d  (%d employees)", currentPage, totalPages, totalFilteredRecords));
        prevPageBtn.setDisable(currentPage <= 1);
        nextPageBtn.setDisable(currentPage >= totalPages);
    }

    private VBox buildEmployeeCard(Employee emp) {
        VBox card = new VBox(10);
        card.getStyleClass().add("employee-card");
        card.setPrefWidth(265);
        card.setMaxWidth(300);

        // Header Row: Name / ID & Department Badge
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(emp.getName());
        name.getStyleClass().add("card-title");
        name.setTooltip(new Tooltip("ID #" + emp.getId()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label deptBadge = createDeptBadge(emp.getDepartment());
        top.getChildren().addAll(name, spacer, deptBadge);

        // Role & ID info
        Label idRole = new Label(String.format("ID #%d • %s", emp.getId(), emp.getJobRole()));
        idRole.getStyleClass().add("card-prop-label");

        // Income & Overtime Row
        HBox salaryRow = new HBox(8);
        salaryRow.setAlignment(Pos.CENTER_LEFT);

        Label salary = new Label("₹" + money.format(emp.getMonthlyIncome()) + "/mo");
        salary.setStyle("-fx-text-fill: #34d399; -fx-font-weight: 700; -fx-font-size: 14px;");

        Region sSpacer = new Region();
        HBox.setHgrow(sSpacer, Priority.ALWAYS);

        Label otBadge = new Label("Yes".equalsIgnoreCase(emp.getOvertime()) ? "Overtime" : "Standard");
        otBadge.getStyleClass().addAll("badge", "Yes".equalsIgnoreCase(emp.getOvertime()) ? "badge-overtime-yes" : "badge-overtime-no");

        salaryRow.getChildren().addAll(salary, sSpacer, otBadge);

        // Stats Row: Age, Satisfaction, Tenure
        String stars = "★".repeat(Math.max(1, Math.min(4, emp.getJobSatisfaction()))) + "☆".repeat(Math.max(0, 4 - emp.getJobSatisfaction()));
        Label stats = new Label(String.format("Age: %d yrs  •  Tenure: %d yrs  •  Sat: %s", emp.getAge(), emp.getYearsAtCompany(), stars));
        stats.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        // Action Buttons Row
        HBox actionRow = new HBox(8);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        Button viewBtn = new Button("View");
        viewBtn.getStyleClass().add("secondary-button");
        viewBtn.setOnAction(e -> showEmployeeDetails(emp));

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setOnAction(e -> showEditEmployeeDialog(emp));

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().add("danger-button");
        delBtn.setOnAction(e -> confirmDeleteEmployee(emp));

        actionRow.getChildren().addAll(viewBtn, editBtn, delBtn);

        card.getChildren().addAll(top, idRole, salaryRow, stats, new Separator(), actionRow);
        return card;
    }

    private Label createDeptBadge(String dept) {
        Label badge = new Label(dept != null ? dept : "Unknown");
        badge.getStyleClass().add("badge");
        if (dept != null) {
            if (dept.contains("Research") || dept.contains("Development") || dept.contains("Engineering")) {
                badge.getStyleClass().add("badge-dept-rnd");
            } else if (dept.contains("Sales")) {
                badge.getStyleClass().add("badge-dept-sales");
            } else {
                badge.getStyleClass().add("badge-dept-hr");
            }
        }
        return badge;
    }

    // =========================================================
    // TABLE VIEW
    // =========================================================

    private VBox buildTableViewWrapper() {
        VBox wrapper = new VBox(10);
        VBox.setVgrow(wrapper, Priority.ALWAYS);

        employeeTableView = new TableView<>();
        employeeTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(employeeTableView, Priority.ALWAYS);

        TableColumn<Employee, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(65);

        TableColumn<Employee, String> nameCol = new TableColumn<>("Employee Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(160);

        TableColumn<Employee, Integer> ageCol = new TableColumn<>("Age");
        ageCol.setCellValueFactory(new PropertyValueFactory<>("age"));
        ageCol.setPrefWidth(60);

        TableColumn<Employee, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(new PropertyValueFactory<>("department"));
        deptCol.setPrefWidth(160);

        TableColumn<Employee, String> roleCol = new TableColumn<>("Job Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("jobRole"));
        roleCol.setPrefWidth(170);

        TableColumn<Employee, String> salaryCol = new TableColumn<>("Monthly Income");
        salaryCol.setCellValueFactory(cell -> new SimpleStringProperty("₹" + money.format(cell.getValue().getMonthlyIncome())));
        salaryCol.setPrefWidth(120);

        TableColumn<Employee, String> otCol = new TableColumn<>("Overtime");
        otCol.setCellValueFactory(new PropertyValueFactory<>("overtime"));
        otCol.setPrefWidth(85);

        TableColumn<Employee, Integer> satCol = new TableColumn<>("Job Sat.");
        satCol.setCellValueFactory(new PropertyValueFactory<>("jobSatisfaction"));
        satCol.setPrefWidth(80);

        TableColumn<Employee, Integer> yearsCol = new TableColumn<>("Years");
        yearsCol.setCellValueFactory(new PropertyValueFactory<>("yearsAtCompany"));
        yearsCol.setPrefWidth(70);

        TableColumn<Employee, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(180);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Delete");
            private final HBox pane = new HBox(6, editBtn, delBtn);

            {
                editBtn.getStyleClass().add("secondary-button");
                delBtn.getStyleClass().add("danger-button");
                pane.setAlignment(Pos.CENTER);

                editBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    showEditEmployeeDialog(emp);
                });

                delBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    confirmDeleteEmployee(emp);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        employeeTableView.getColumns().addAll(idCol, nameCol, ageCol, deptCol, roleCol, salaryCol, otCol, satCol, yearsCol, actionsCol);

        wrapper.getChildren().add(employeeTableView);
        return wrapper;
    }

    private void loadTableData() {
        List<Employee> list = queryEmployees(false);
        ObservableList<Employee> data = FXCollections.observableArrayList(list);
        employeeTableView.setItems(data);
    }

    // =========================================================
    // ANALYTICS & INSIGHTS VIEW
    // =========================================================

    private VBox buildAnalyticsView() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(10));
        VBox.setVgrow(box, Priority.ALWAYS);

        HBox chartRow = new HBox(16);
        chartRow.setAlignment(Pos.CENTER);
        VBox.setVgrow(chartRow, Priority.ALWAYS);

        // Chart 1: Department Distribution PieChart
        PieChart deptChart = new PieChart();
        deptChart.setTitle("Staff Distribution by Department");
        deptChart.getStyleClass().add("chart");
        deptChart.setLegendVisible(true);
        HBox.setHgrow(deptChart, Priority.ALWAYS);

        // Chart 2: Average Salary by Department BarChart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Department");
        yAxis.setLabel("Avg. Salary (₹)");
        BarChart<String, Number> salaryChart = new BarChart<>(xAxis, yAxis);
        salaryChart.setTitle("Average Monthly Salary by Department");
        salaryChart.getStyleClass().add("chart");
        salaryChart.setLegendVisible(false);
        HBox.setHgrow(salaryChart, Priority.ALWAYS);

        chartRow.getChildren().addAll(deptChart, salaryChart);

        HBox chartRow2 = new HBox(16);
        chartRow2.setAlignment(Pos.CENTER);
        VBox.setVgrow(chartRow2, Priority.ALWAYS);

        // Chart 3: Job Satisfaction Breakdown PieChart
        PieChart satChart = new PieChart();
        satChart.setTitle("Workforce Job Satisfaction Levels");
        satChart.getStyleClass().add("chart");
        satChart.setLegendVisible(true);
        HBox.setHgrow(satChart, Priority.ALWAYS);

        // Chart 4: Overtime Distribution PieChart
        PieChart otChart = new PieChart();
        otChart.setTitle("Overtime Workload Ratio");
        otChart.getStyleClass().add("chart");
        otChart.setLegendVisible(true);
        HBox.setHgrow(otChart, Priority.ALWAYS);

        chartRow2.getChildren().addAll(satChart, otChart);

        box.getChildren().addAll(chartRow, chartRow2);
        return box;
    }

    private void refreshAnalytics() {
        if (analyticsContainer == null || analyticsContainer.getChildren().size() < 2) return;

        HBox row1 = (HBox) analyticsContainer.getChildren().get(0);
        PieChart deptChart = (PieChart) row1.getChildren().get(0);
        @SuppressWarnings("unchecked")
        BarChart<String, Number> salaryChart = (BarChart<String, Number>) row1.getChildren().get(1);

        HBox row2 = (HBox) analyticsContainer.getChildren().get(1);
        PieChart satChart = (PieChart) row2.getChildren().get(0);
        PieChart otChart = (PieChart) row2.getChildren().get(1);

        String roleConstraint = getRoleConstraint();

        // 1. Dept Pie Chart
        ObservableList<PieChart.Data> deptData = FXCollections.observableArrayList();
        String deptSql = "SELECT department, COUNT(*) AS cnt FROM employees WHERE 1=1 " + roleConstraint + " GROUP BY department";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(deptSql)) {
            while (rs.next()) {
                deptData.add(new PieChart.Data(rs.getString("department") + " (" + rs.getInt("cnt") + ")", rs.getInt("cnt")));
            }
        } catch (SQLException ignored) {}
        deptChart.setData(deptData);

        // 2. Avg Salary Bar Chart
        salaryChart.getData().clear();
        XYChart.Series<String, Number> salarySeries = new XYChart.Series<>();
        salarySeries.setName("Average Monthly Salary");
        String salSql = "SELECT department, AVG(monthly_income) AS avg_sal FROM employees WHERE 1=1 " + roleConstraint + " GROUP BY department";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(salSql)) {
            while (rs.next()) {
                salarySeries.getData().add(new XYChart.Data<>(rs.getString("department"), rs.getDouble("avg_sal")));
            }
        } catch (SQLException ignored) {}
        salaryChart.getData().add(salarySeries);

        // 3. Satisfaction Pie Chart
        ObservableList<PieChart.Data> satData = FXCollections.observableArrayList();
        String satSql = "SELECT job_satisfaction, COUNT(*) AS cnt FROM employees WHERE 1=1 " + roleConstraint + " GROUP BY job_satisfaction ORDER BY job_satisfaction";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(satSql)) {
            while (rs.next()) {
                int level = rs.getInt("job_satisfaction");
                String label = switch (level) {
                    case 1 -> "Low (1/4)";
                    case 2 -> "Medium (2/4)";
                    case 3 -> "High (3/4)";
                    case 4 -> "Very High (4/4)";
                    default -> "Level " + level;
                };
                satData.add(new PieChart.Data(label + " (" + rs.getInt("cnt") + ")", rs.getInt("cnt")));
            }
        } catch (SQLException ignored) {}
        satChart.setData(satData);

        // 4. Overtime Pie Chart
        ObservableList<PieChart.Data> otData = FXCollections.observableArrayList();
        String otSql = "SELECT overtime, COUNT(*) AS cnt FROM employees WHERE 1=1 " + roleConstraint + " GROUP BY overtime";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(otSql)) {
            while (rs.next()) {
                String ot = rs.getString("overtime");
                String label = "Yes".equalsIgnoreCase(ot) ? "Overtime Enrolled" : "Standard Hours";
                otData.add(new PieChart.Data(label + " (" + rs.getInt("cnt") + ")", rs.getInt("cnt")));
            }
        } catch (SQLException ignored) {}
        otChart.setData(otData);
    }

    // =========================================================
    // QUERY & DATA SYNC
    // =========================================================

    private String getRoleConstraint() {
        if ("ENGINEERING_LEAD".equals(currentRole)) {
            return " AND (department = 'Engineering' OR department = 'Research & Development') ";
        }
        return " ";
    }

    private List<Employee> queryEmployees(boolean paginate) {
        List<Employee> list = new ArrayList<>();
        String search = searchField != null ? searchField.getText().trim() : "";
        String dept = departmentFilter != null ? departmentFilter.getValue() : "All Departments";
        String ot = overtimeFilter != null ? overtimeFilter.getValue() : "All Overtime";
        String sort = sortOrderFilter != null ? sortOrderFilter.getValue() : "Sort: ID (Asc)";

        StringBuilder sql = new StringBuilder("SELECT * FROM employees WHERE 1=1 ");
        sql.append(getRoleConstraint());

        List<Object> params = new ArrayList<>();

        if (!search.isEmpty()) {
            sql.append(" AND (CAST(id AS TEXT) LIKE ? OR lower(name) LIKE lower(?) OR lower(department) LIKE lower(?) OR lower(job_role) LIKE lower(?)) ");
            String pat = "%" + search + "%";
            params.add(pat);
            params.add(pat);
            params.add(pat);
            params.add(pat);
        }

        if (dept != null && !"All Departments".equals(dept)) {
            sql.append(" AND department = ? ");
            params.add(dept);
        }

        if (ot != null && !"All Overtime".equals(ot)) {
            sql.append(" AND overtime = ? ");
            params.add(ot.contains("Yes") ? "Yes" : "No");
        }

        // Count total matching records
        String countSql = "SELECT COUNT(*) FROM (" + sql + ")";
        try (PreparedStatement psCount = connection.prepareStatement(countSql)) {
            for (int i = 0; i < params.size(); i++) {
                psCount.setObject(i + 1, params.get(i));
            }
            try (ResultSet rsCount = psCount.executeQuery()) {
                if (rsCount.next()) {
                    totalFilteredRecords = rsCount.getInt(1);
                }
            }
        } catch (SQLException ignored) {}

        // Sorting
        sql.append(" ORDER BY ");
        if (sort != null) {
            switch (sort) {
                case "Sort: ID (Desc)" -> sql.append("id DESC");
                case "Sort: Name (A-Z)" -> sql.append("name ASC");
                case "Sort: Salary (High - Low)" -> sql.append("monthly_income DESC");
                case "Sort: Salary (Low - High)" -> sql.append("monthly_income ASC");
                case "Sort: Experience (Years)" -> sql.append("years_at_company DESC");
                default -> sql.append("id ASC");
            }
        } else {
            sql.append("id ASC");
        }

        if (paginate) {
            int offset = Math.max(0, (currentPage - 1) * pageSize);
            sql.append(" LIMIT ").append(pageSize).append(" OFFSET ").append(offset);
        }

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Employee(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("age"),
                            rs.getString("department"),
                            rs.getString("job_role"),
                            rs.getDouble("monthly_income"),
                            rs.getString("overtime"),
                            rs.getInt("job_satisfaction"),
                            rs.getInt("years_at_company")
                    ));
                }
            }
        } catch (SQLException e) {
            showError("Query error: " + e.getMessage());
        }

        return list;
    }

    private void refreshDashboardData() {
        updateKpis();
        loadCards();
        loadTableData();
        if (activeView == ViewMode.ANALYTICS) {
            refreshAnalytics();
        }
    }

    private void updateKpis() {
        String roleConstraint = getRoleConstraint();
        String kpiSql = """
                SELECT
                    COUNT(*) AS total,
                    COALESCE(SUM(CASE WHEN overtime = 'Yes' THEN 1 ELSE 0 END), 0) AS overtime_count,
                    COALESCE(AVG(monthly_income), 0) AS avg_income,
                    COALESCE(SUM(monthly_income), 0) AS total_payroll,
                    COUNT(DISTINCT department) AS dept_count
                FROM employees
                WHERE 1=1
                """ + roleConstraint;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(kpiSql)) {
            if (rs.next()) {
                int total = rs.getInt("total");
                int otCount = rs.getInt("overtime_count");
                double avgInc = rs.getDouble("avg_income");
                double totPay = rs.getDouble("total_payroll");
                int deptCount = rs.getInt("dept_count");

                totalEmployeesKpi.setText(money.format(total));
                totalDepartmentsKpi.setText(String.valueOf(deptCount));
                overtimeKpi.setText(String.format("%s (%d%%)", money.format(otCount), total > 0 ? (otCount * 100 / total) : 0));
                avgIncomeKpi.setText("₹" + money.format(avgInc));
                totalPayrollKpi.setText("₹" + money.format(totPay));
            }
        } catch (SQLException ignored) {}
    }

    // =========================================================
    // CRUD OPERATIONS (CREATE, UPDATE, DELETE)
    // =========================================================

    private void showAddEmployeeDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add New Employee");
        dialog.setHeaderText("Create a new personnel profile in the database");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        TextField nameField = new TextField();
        nameField.setPromptText("Full Name");
        nameField.getStyleClass().add("field");

        TextField ageField = new TextField("28");
        ageField.getStyleClass().add("field");

        ComboBox<String> deptBox = new ComboBox<>();
        deptBox.getItems().addAll("Research & Development", "Sales", "Human Resources");
        deptBox.setValue("Research & Development");
        deptBox.getStyleClass().add("combo-box");

        TextField roleField = new TextField("Research Scientist");
        roleField.getStyleClass().add("field");

        TextField incomeField = new TextField("5500");
        incomeField.getStyleClass().add("field");

        ComboBox<String> otBox = new ComboBox<>();
        otBox.getItems().addAll("No", "Yes");
        otBox.setValue("No");
        otBox.getStyleClass().add("combo-box");

        Slider satSlider = new Slider(1, 4, 3);
        satSlider.setShowTickMarks(true);
        satSlider.setShowTickLabels(true);
        satSlider.setMajorTickUnit(1);
        satSlider.setBlockIncrement(1);
        satSlider.setSnapToTicks(true);

        TextField yearsField = new TextField("2");
        yearsField.getStyleClass().add("field");

        grid.add(new Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Age:"), 0, 1);
        grid.add(ageField, 1, 1);
        grid.add(new Label("Department:"), 0, 2);
        grid.add(deptBox, 1, 2);
        grid.add(new Label("Job Role:"), 0, 3);
        grid.add(roleField, 1, 3);
        grid.add(new Label("Monthly Income (₹):"), 0, 4);
        grid.add(incomeField, 1, 4);
        grid.add(new Label("Overtime:"), 0, 5);
        grid.add(otBox, 1, 5);
        grid.add(new Label("Job Satisfaction (1-4):"), 0, 6);
        grid.add(satSlider, 1, 6);
        grid.add(new Label("Years at Company:"), 0, 7);
        grid.add(yearsField, 1, 7);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                showError("Employee name cannot be empty.");
                return;
            }

            try {
                int nextId = 1;
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM employees")) {
                    if (rs.next()) nextId = rs.getInt(1);
                }

                String sql = "INSERT INTO employees (id, name, age, department, job_role, monthly_income, overtime, job_satisfaction, years_at_company) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, nextId);
                    ps.setString(2, name);
                    ps.setInt(3, parseIntSafe(ageField.getText(), 30));
                    ps.setString(4, deptBox.getValue());
                    ps.setString(5, roleField.getText().trim());
                    ps.setDouble(6, parseDoubleSafe(incomeField.getText(), 5000));
                    ps.setString(7, otBox.getValue());
                    ps.setInt(8, (int) Math.round(satSlider.getValue()));
                    ps.setInt(9, parseIntSafe(yearsField.getText(), 1));
                    ps.executeUpdate();
                }

                refreshDashboardData();
                showInfo("Employee Added", "Employee #" + nextId + " (" + name + ") successfully registered.");
            } catch (SQLException ex) {
                showError("Error adding employee: " + ex.getMessage());
            }
        }
    }

    private void showEditEmployeeDialog(Employee emp) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Employee #" + emp.getId());
        dialog.setHeaderText("Modify details for " + emp.getName());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        TextField nameField = new TextField(emp.getName());
        nameField.getStyleClass().add("field");

        TextField ageField = new TextField(String.valueOf(emp.getAge()));
        ageField.getStyleClass().add("field");

        ComboBox<String> deptBox = new ComboBox<>();
        deptBox.getItems().addAll("Research & Development", "Sales", "Human Resources");
        deptBox.setValue(emp.getDepartment());
        deptBox.getStyleClass().add("combo-box");

        TextField roleField = new TextField(emp.getJobRole());
        roleField.getStyleClass().add("field");

        TextField incomeField = new TextField(String.valueOf((int) emp.getMonthlyIncome()));
        incomeField.getStyleClass().add("field");

        ComboBox<String> otBox = new ComboBox<>();
        otBox.getItems().addAll("No", "Yes");
        otBox.setValue(emp.getOvertime());
        otBox.getStyleClass().add("combo-box");

        Slider satSlider = new Slider(1, 4, emp.getJobSatisfaction());
        satSlider.setShowTickMarks(true);
        satSlider.setShowTickLabels(true);
        satSlider.setMajorTickUnit(1);
        satSlider.setSnapToTicks(true);

        TextField yearsField = new TextField(String.valueOf(emp.getYearsAtCompany()));
        yearsField.getStyleClass().add("field");

        grid.add(new Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Age:"), 0, 1);
        grid.add(ageField, 1, 1);
        grid.add(new Label("Department:"), 0, 2);
        grid.add(deptBox, 1, 2);
        grid.add(new Label("Job Role:"), 0, 3);
        grid.add(roleField, 1, 3);
        grid.add(new Label("Monthly Income (₹):"), 0, 4);
        grid.add(incomeField, 1, 4);
        grid.add(new Label("Overtime:"), 0, 5);
        grid.add(otBox, 1, 5);
        grid.add(new Label("Job Satisfaction (1-4):"), 0, 6);
        grid.add(satSlider, 1, 6);
        grid.add(new Label("Years at Company:"), 0, 7);
        grid.add(yearsField, 1, 7);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String sql = """
                    UPDATE employees
                    SET name = ?, age = ?, department = ?, job_role = ?,
                        monthly_income = ?, overtime = ?, job_satisfaction = ?, years_at_company = ?
                    WHERE id = ?
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, nameField.getText().trim());
                ps.setInt(2, parseIntSafe(ageField.getText(), emp.getAge()));
                ps.setString(3, deptBox.getValue());
                ps.setString(4, roleField.getText().trim());
                ps.setDouble(5, parseDoubleSafe(incomeField.getText(), emp.getMonthlyIncome()));
                ps.setString(6, otBox.getValue());
                ps.setInt(7, (int) Math.round(satSlider.getValue()));
                ps.setInt(8, parseIntSafe(yearsField.getText(), emp.getYearsAtCompany()));
                ps.setInt(9, emp.getId());
                ps.executeUpdate();

                refreshDashboardData();
                showInfo("Record Updated", "Employee #" + emp.getId() + " updated successfully.");
            } catch (SQLException ex) {
                showError("Update failed: " + ex.getMessage());
            }
        }
    }

    private void confirmDeleteEmployee(Employee emp) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete Employee #" + emp.getId() + " (" + emp.getName() + ")?");
        alert.setContentText("This action will permanently remove this record from the database.");

        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM employees WHERE id = ?")) {
                ps.setInt(1, emp.getId());
                ps.executeUpdate();

                refreshDashboardData();
                showInfo("Deleted", "Employee record #" + emp.getId() + " was deleted.");
            } catch (SQLException ex) {
                showError("Failed to delete record: " + ex.getMessage());
            }
        }
    }

    private void showEmployeeDetails(Employee emp) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Personnel Profile");
        dialog.setHeaderText("Employee Profile: " + emp.getName());

        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("employee-card");
        box.setMinWidth(360);

        String stars = "★".repeat(Math.max(1, Math.min(4, emp.getJobSatisfaction()))) + "☆".repeat(Math.max(0, 4 - emp.getJobSatisfaction()));

        box.getChildren().addAll(
                new Label("Employee ID: #" + emp.getId()),
                new Label("Full Name: " + emp.getName()),
                new Label("Age: " + emp.getAge() + " years"),
                new Label("Department: " + emp.getDepartment()),
                new Label("Job Role: " + emp.getJobRole()),
                new Label("Monthly Income: ₹" + money.format(emp.getMonthlyIncome())),
                new Label("Annual Compensation: ₹" + money.format(emp.getMonthlyIncome() * 12)),
                new Label("Overtime Status: " + emp.getOvertime()),
                new Label("Job Satisfaction: " + stars + " (" + emp.getJobSatisfaction() + "/4)"),
                new Label("Years at Company: " + emp.getYearsAtCompany() + " years")
        );

        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // =========================================================
    // EXPORT TO CSV
    // =========================================================

    private void exportToCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Employees to CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        chooser.setInitialFileName("Employees_Export.csv");

        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;

        List<Employee> allEmployees = queryEmployees(false);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.println("EmployeeNumber,EmployeeName,Age,Department,JobRole,MonthlyIncome,OverTime,JobSatisfaction,YearsAtCompany");
            for (Employee emp : allEmployees) {
                writer.printf("%d,\"%s\",%d,\"%s\",\"%s\",%.2f,\"%s\",%d,%d%n",
                        emp.getId(),
                        emp.getName().replace("\"", "\"\""),
                        emp.getAge(),
                        emp.getDepartment(),
                        emp.getJobRole(),
                        emp.getMonthlyIncome(),
                        emp.getOvertime(),
                        emp.getJobSatisfaction(),
                        emp.getYearsAtCompany()
                );
            }
            showInfo("Export Complete", "Successfully exported " + allEmployees.size() + " records to " + file.getName());
        } catch (Exception ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    // =========================================================
    // UTILITY & LIFECYCLE
    // =========================================================

    private void addCss(Scene scene) {
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Error");
        alert.setHeaderText("Operation Encountered an Error");
        alert.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showFatalError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Fatal Error");
        alert.setHeaderText("Application could not start");
        alert.showAndWait();
    }

    @Override
    public void stop() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    // =========================================================
    // EMPLOYEE MODEL (JavaFX Property-Compatible)
    // =========================================================

    public static class Employee {
        private int id;
        private String name;
        private int age;
        private String department;
        private String jobRole;
        private double monthlyIncome;
        private String overtime;
        private int jobSatisfaction;
        private int yearsAtCompany;

        public Employee(int id, String name, int age, String department, String jobRole,
                        double monthlyIncome, String overtime, int jobSatisfaction, int yearsAtCompany) {
            this.id = id;
            this.name = (name != null && !name.isBlank()) ? name : "Employee #" + id;
            this.age = age;
            this.department = department;
            this.jobRole = jobRole;
            this.monthlyIncome = monthlyIncome;
            this.overtime = overtime;
            this.jobSatisfaction = jobSatisfaction;
            this.yearsAtCompany = yearsAtCompany;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }

        public double getMonthlyIncome() { return monthlyIncome; }
        public void setMonthlyIncome(double monthlyIncome) { this.monthlyIncome = monthlyIncome; }

        public String getOvertime() { return overtime; }
        public void setOvertime(String overtime) { this.overtime = overtime; }

        public int getJobSatisfaction() { return jobSatisfaction; }
        public void setJobSatisfaction(int jobSatisfaction) { this.jobSatisfaction = jobSatisfaction; }

        public int getYearsAtCompany() { return yearsAtCompany; }
        public void setYearsAtCompany(int yearsAtCompany) { this.yearsAtCompany = yearsAtCompany; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
