import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.table.DefaultTableModel;

// ========================================================
//     DESKTOP WORKOUT TRACKER (TEMPLATE + FIXED DROPDOWN)
// ========================================================

public class GymTracker {

    // ========================================================
    // STEP 1-5: Data Classes
    // ========================================================

    static abstract class Exercise implements Serializable {
        protected String name;
        protected String muscleGroup;
        public Exercise(String name, String muscleGroup) {
            this.name = name; this.muscleGroup = muscleGroup;
        }
        public String getName() { return name; }
        public String getMuscleGroup() { return muscleGroup; }
        public abstract double getPerformanceMetric();
    }

    static class StrengthExercise extends Exercise implements Serializable {
        private int sets;
        private int reps;
        private double weight;
        public StrengthExercise(String name, String muscleGroup, int sets, int reps, double weight) {
            super(name, muscleGroup);
            this.sets = sets; this.reps = reps; this.weight = weight;
        }
        public double getVolume() { return sets * reps * weight; }
        @Override
        public double getPerformanceMetric() { return weight * (1 + reps / 30.0); } // Estimated 1RM
        public int getSets() { return sets; }
        public int getReps() { return reps; }
        public double getWeight() { return weight; }
    }

    static class WorkoutEntry implements Serializable {
        private StrengthExercise exercise;
        private LocalDateTime timestamp;
        public WorkoutEntry(StrengthExercise exercise) {
            this.exercise = exercise; this.timestamp = LocalDateTime.now();
        }
        public StrengthExercise getExercise() { return exercise; }
        public double getVolume() { return exercise.getVolume(); }
    }

    static class WorkoutSession implements Serializable {
        private LocalDateTime date;
        private java.util.ArrayList<WorkoutEntry> entries;
        public WorkoutSession() {
            this.date = LocalDateTime.now();
            this.entries = new java.util.ArrayList<>();
        }
        public void addEntry(WorkoutEntry entry) { entries.add(entry); }
        public java.util.List<WorkoutEntry> getEntries() { return entries; }
        public LocalDateTime getDate() { return date; }
        public double getTotalVolume() {
            double total = 0;
            for (WorkoutEntry e : entries) total += e.getVolume();
            return total;
        }
        public String getSummary() {
            return "Session on " + date.toLocalDate() + "\nTotal Volume: " + getTotalVolume();
        }
    }

    static class ProgressTracker implements Serializable {
        private java.util.ArrayList<WorkoutSession> history;
        public ProgressTracker() { history = new java.util.ArrayList<>(); }
        public void addSession(WorkoutSession session) { history.add(0, session); }
        public int getTotalSessions() { return history.size(); }
        public double getTotalVolumeAllTime() {
            double total = 0;
            for (WorkoutSession s : history) total += s.getTotalVolume();
            return total;
        }
        public java.util.List<WorkoutSession> getHistory() { return history; }
        public double getExercisePR(String exerciseName) {
            double highest1RM = 0;
            for (WorkoutSession session : history) {
                for (WorkoutEntry entry : session.getEntries()) {
                    StrengthExercise ex = entry.getExercise();
                    if (ex.getName().equalsIgnoreCase(exerciseName)) {
                        double oneRM = ex.getPerformanceMetric();
                        if (oneRM > highest1RM) highest1RM = oneRM;
                    }
                }
            }
            return highest1RM;
        }
    }

    // ========================================================
    // STEP 6: ExerciseLibrary & WorkoutTemplate
    // ========================================================
    
    static class WorkoutTemplate {
        String name;
        String[] exercises;

        public WorkoutTemplate(String name, String[] exercises) {
            this.name = name;
            this.exercises = exercises;
        }

        public String getName() { return name; }
        public String getSummary() {
            if (exercises.length == 0) return "No exercises defined";
            return exercises[0] + (exercises.length > 1 ? ", " + exercises[1] + ", ..." : "");
        }
    }
    
    static class ExerciseLibrary {
        private java.util.ArrayList<String> exercises;
        private java.util.List<WorkoutTemplate> templates;

        public ExerciseLibrary() {
            exercises = new java.util.ArrayList<>();
            exercises.add("Triceps Extension (Arms)");
            exercises.add("Lateral Raise (Shoulders)");
            exercises.add("Incline Chest Press (Chest)");
            exercises.add("Bicep Curl (Arms)");
            exercises.add("Preacher Curl (Arms)");
            exercises.add("Squat (Legs)");
            exercises.add("Deadlift (Back)");
            exercises.add("Leg Press (Legs)");

            templates = new ArrayList<>();
            templates.add(new WorkoutTemplate("Upper Body", new String[]{
                "Incline Chest Press (Chest)", 
                "Lat Pulldown (Back)", 
                "Shoulder Press (Shoulders)"
            }));
            templates.add(new WorkoutTemplate("Lower Body", new String[]{
                "Squat (Legs)", 
                "Deadlift (Back)", 
                "Leg Press (Legs)"
            }));
            templates.add(new WorkoutTemplate("Full Body", new String[]{
                "Squat (Legs)", 
                "Bench Press (Chest)", 
                "Deadlift (Back)"
            }));
        }
        public java.util.List<String> getExercises() { return exercises; }
        public java.util.List<WorkoutTemplate> getTemplates() { return templates; }
    }


    // ========================================================
    // STEP 7: GUI
    // ========================================================
    static class GymTrackerGUI extends JFrame {

        private static final String PROGRESS_FILE = "progress.dat";
        private ExerciseLibrary library = new ExerciseLibrary();
        private ProgressTracker tracker;
        
        private WorkoutSession currentSession;
        private DefaultTableModel currentWorkoutTableModel;
        private ScheduledExecutorService scheduler;
        private long startTime;
        private JLabel timerLabel;

        private JTabbedPane tabbedPane;
        private JTextArea historyTextArea;
        private JTextArea progressStatsArea;

        private JComboBox<String> exerciseDropdown;
        private JTextField setsField, repsField, weightField;
        
        private final Color DARK_BACKGROUND = new Color(28, 28, 30);
        private final Color CARD_BACKGROUND = new Color(45, 45, 50);
        private final Color TEXT_COLOR = new Color(240, 240, 240);
        private final Color ACCENT_BLUE = new Color(50, 150, 255);
        private final Color ACCENT_RED = new Color(255, 69, 58);

        public GymTrackerGUI() {
            loadProgress();
            currentSession = new WorkoutSession();

            setTitle("Sculpt");
            setSize(900, 700);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Set look & feel and override Nimbus dropdown colors to force readable popup
            try {
                UIManager.setLookAndFeel(new NimbusLookAndFeel());

                // Keep general Nimbus tweaks
                UIManager.put("control", DARK_BACKGROUND);
                UIManager.put("nimbusBase", new Color(60, 60, 60));
                UIManager.put("nimbusBlueGrey", new Color(45, 45, 45));
                UIManager.put("text", TEXT_COLOR);

                // Override comboBox popup & renderer defaults for readable popup
                UIManager.put("ComboBox.popupBackground", Color.WHITE);
                UIManager.put("ComboBox.background", Color.WHITE);
                UIManager.put("ComboBox.foreground", Color.BLACK);
                UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Enabled].background", Color.WHITE);
                UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Selected].background", new Color(220, 240, 255));
                UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Enabled].textForeground", Color.BLACK);
                UIManager.put("ComboBox:\"ComboBox.listRenderer\"[Selected].textForeground", Color.BLACK);
                // --- after setting NimbusLookAndFeel and before creating components ---
                UIManager.put("TextField.background", Color.BLACK);
                UIManager.put("TextField.foreground", Color.WHITE);
                UIManager.put("TextField.caretForeground", Color.WHITE);
                UIManager.put("TextField.inactiveForeground", Color.WHITE);
                UIManager.put("TextField.selectionBackground", new Color(40, 40, 40));
                UIManager.put("TextField.selectionForeground", Color.WHITE);

// also ensure combo editor follows same defaults
                UIManager.put("ComboBox.background", Color.WHITE); // editor field we'll style manually
                UIManager.put("ComboBox.foreground", Color.BLACK);


            } catch (Exception ignored) {}
            
            getContentPane().setBackground(DARK_BACKGROUND);
            
            JLabel titleLabel = new JLabel("Sculpt", SwingConstants.CENTER);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
            titleLabel.setForeground(TEXT_COLOR);
            titleLabel.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
            add(titleLabel, BorderLayout.NORTH);

            tabbedPane = createTabbedPane();
            tabbedPane.addTab("💪 Workout", createWorkoutScreen());
            tabbedPane.addTab("🗓️ History", createHistoryScreen());
            tabbedPane.addTab("🏆 Progress", createProgressScreen());
            add(tabbedPane, BorderLayout.CENTER);

            updateHistoryAndProgressDisplays();
            setLocationRelativeTo(null);
            setVisible(true);
        }
        
        private JTabbedPane createTabbedPane() {
            JTabbedPane tp = new JTabbedPane();
            tp.setFont(new Font("Segoe UI", Font.BOLD, 14));
            tp.setForeground(TEXT_COLOR);
            tp.setBackground(new Color(38, 38, 40));
            tp.setOpaque(true);
            tp.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
            return tp;
        }

        private JPanel createWorkoutScreen() {
            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(DARK_BACKGROUND);
            
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createTemplatePanel(), createActiveSessionPanel());
            splitPane.setDividerLocation(300);
            splitPane.setDividerSize(5);
            splitPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            splitPane.setBackground(DARK_BACKGROUND);
            
            mainPanel.add(splitPane, BorderLayout.CENTER);
            return mainPanel;
        }

        private JPanel createTemplatePanel() {
            JPanel leftPanel = new JPanel();
            leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
            leftPanel.setBackground(DARK_BACKGROUND);
            leftPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));
            
            JLabel templatesTitle = createStyledLabel("My Templates:", 18, true);
            templatesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            templatesTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            leftPanel.add(templatesTitle);
            
            JButton startButton = new JButton("START EMPTY SESSION");
            stylePrimaryButton(startButton);
            startButton.addActionListener(e -> startNewSession("Custom Workout"));
            startButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            startButton.setMaximumSize(new Dimension(300, 45));
            leftPanel.add(startButton);
            leftPanel.add(Box.createVerticalStrut(20));
            
            for (WorkoutTemplate template : library.getTemplates()) {
                JButton button = createTemplateButton(template);
                leftPanel.add(button);
                leftPanel.add(Box.createVerticalStrut(10));
            }
            
            JScrollPane scrollPane = new JScrollPane(leftPanel);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setBackground(DARK_BACKGROUND);
            
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(scrollPane, BorderLayout.CENTER);
            return wrapper;
        }

        private JButton createTemplateButton(WorkoutTemplate template) {
            JButton button = new JButton("<html><b>" + template.getName() + "</b><br><small style='color:#AAAAAA;'>" + template.getSummary() + "</small></html>");
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setBackground(CARD_BACKGROUND);
            button.setForeground(TEXT_COLOR);
            button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(60, 60, 60)),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));

            // Load template exercises when clicked
            button.addActionListener(e -> {
                startNewSession(template.getName());
                loadTemplateIntoSession(template);
            });

            button.setMaximumSize(new Dimension(300, 80));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            return button;
        }

        // Load template exercises into current session
        private void loadTemplateIntoSession(WorkoutTemplate template) {
            currentWorkoutTableModel.setRowCount(0);
            currentSession = new WorkoutSession();

            for (String ex : template.exercises) {
                String[] parts = ex.split(" \\(");
                String name = parts[0];
                String muscle = parts.length > 1 ? parts[1].replace(")", "") : "";

                StrengthExercise exercise = new StrengthExercise(name, muscle, 0, 0, 0);
                WorkoutEntry entry = new WorkoutEntry(exercise);

                currentSession.addEntry(entry);

                currentWorkoutTableModel.addRow(new Object[]{
                        name, "-", "-", "-", "-"
                });
            }
        }

        private JPanel createActiveSessionPanel() {
            JPanel rightPanel = new JPanel(new BorderLayout(15, 15));
            rightPanel.setBackground(CARD_BACKGROUND);
            rightPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            
            JPanel topControl = new JPanel(new BorderLayout());
            topControl.setBackground(CARD_BACKGROUND);
            
            timerLabel = createStyledLabel("0:00", 36, true);
            topControl.add(timerLabel, BorderLayout.WEST);
            
            JButton finishButton = new JButton("FINISH SESSION");
            finishButton.setBackground(ACCENT_RED);
            finishButton.setForeground(Color.WHITE);
            finishButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
            finishButton.setFocusPainted(false);
            finishButton.addActionListener(e -> finishWorkout());
            topControl.add(finishButton, BorderLayout.EAST);
            
            rightPanel.add(topControl, BorderLayout.NORTH);
            
            JPanel tableContainer = new JPanel(new BorderLayout());
            tableContainer.setBackground(CARD_BACKGROUND);
            JLabel currentSessionTitle = createStyledLabel("Current Session", 16, true);
            tableContainer.add(currentSessionTitle, BorderLayout.NORTH);

            String[] columns = {"Exercise", "Sets", "Reps", "Weight (lb)", "Volume"};
            currentWorkoutTableModel = new DefaultTableModel(columns, 0);
            JTable currentWorkoutTable = new JTable(currentWorkoutTableModel);
            styleDesktopTable(currentWorkoutTable);
            JScrollPane tableScroll = new JScrollPane(currentWorkoutTable);
            tableScroll.getViewport().setBackground(CARD_BACKGROUND);
            tableScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
            tableContainer.add(tableScroll, BorderLayout.CENTER);
            
            rightPanel.add(tableContainer, BorderLayout.CENTER);
            
            JPanel inputPanel = createInputPanel();
            rightPanel.add(inputPanel, BorderLayout.SOUTH);
            
            return rightPanel;
        }

        private JPanel createInputPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 5));
            panel.setBackground(CARD_BACKGROUND);
            panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)), "Add Exercise", 
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, 
                new Font("Segoe UI", Font.BOLD, 14), TEXT_COLOR));
            
            JPanel fieldsPanel = new JPanel(new GridBagLayout());
            fieldsPanel.setBackground(CARD_BACKGROUND);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            
            exerciseDropdown = new JComboBox<>(library.getExercises().toArray(new String[0]));
            // Apply light background and readable text for editor and popup renderer
            styleDropdown(exerciseDropdown);
            fixComboBoxRenderer(exerciseDropdown);

            setsField = createDarkTextField(5);
            repsField = createDarkTextField(5);
            weightField = createDarkTextField(5);
            
            gbc.gridy = 0;
            gbc.weightx = 0.1; gbc.gridx = 0; fieldsPanel.add(createStyledLabel("Exercise:", 12, false), gbc);
            gbc.gridx = 1; fieldsPanel.add(createStyledLabel("Sets:", 12, false), gbc);
            gbc.gridx = 2; fieldsPanel.add(createStyledLabel("Reps:", 12, false), gbc);
            gbc.gridx = 3; fieldsPanel.add(createStyledLabel("Weight (lb):", 12, false), gbc);

            gbc.gridy = 1;
            gbc.weightx = 0.4; gbc.gridx = 0; fieldsPanel.add(exerciseDropdown, gbc);
            gbc.weightx = 0.1; gbc.gridx = 1; fieldsPanel.add(setsField, gbc);
            gbc.gridx = 2; fieldsPanel.add(repsField, gbc);
            gbc.gridx = 3; fieldsPanel.add(weightField, gbc);
            
            JButton addButton = new JButton("ADD SET");
            stylePrimaryButton(addButton);
            addButton.addActionListener(e -> addEntry());
            
            gbc.gridy = 1; gbc.gridx = 4; gbc.weightx = 0.2;
            fieldsPanel.add(addButton, gbc);

            panel.add(fieldsPanel, BorderLayout.CENTER);
            return panel;
        }

        // Custom renderer to ensure the combo popup is white with black text & readable selection
        private void fixComboBoxRenderer(JComboBox<String> box) {
            box.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(
                        JList<?> list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {

                    Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                    if (isSelected) {
                        c.setBackground(new Color(220, 240, 255));
                        c.setForeground(Color.BLACK);
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
                    }

                    return c;
                }
            });
        }

        private JPanel createHistoryScreen() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(DARK_BACKGROUND);
            panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            
            historyTextArea = new JTextArea();
            historyTextArea.setEditable(false);
            historyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            historyTextArea.setBackground(CARD_BACKGROUND);
            historyTextArea.setForeground(TEXT_COLOR);
            historyTextArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            
            JScrollPane scrollPane = new JScrollPane(historyTextArea);
            scrollPane.getViewport().setBackground(DARK_BACKGROUND);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 40)));
            panel.add(scrollPane, BorderLayout.CENTER);
            
            return panel;
        }
        
        private JPanel createProgressScreen() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(DARK_BACKGROUND);
            panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            progressStatsArea = new JTextArea();
            progressStatsArea.setEditable(false);
            progressStatsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
            progressStatsArea.setBackground(CARD_BACKGROUND);
            progressStatsArea.setForeground(TEXT_COLOR);
            progressStatsArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            
            JScrollPane scrollPane = new JScrollPane(progressStatsArea);
            scrollPane.getViewport().setBackground(DARK_BACKGROUND);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 40)));
            panel.add(scrollPane, BorderLayout.CENTER);
            
            return panel;
        }

        private JLabel createStyledLabel(String text, int size, boolean bold) {
            JLabel label = new JLabel(text);
            label.setForeground(TEXT_COLOR);
            int style = bold ? Font.BOLD : Font.PLAIN;
            label.setFont(new Font("Segoe UI", style, size));
            return label;
        }

        private void stylePrimaryButton(JButton button) {
            button.setBackground(ACCENT_BLUE);
            button.setForeground(Color.WHITE);
            button.setFont(new Font("Segoe UI", Font.BOLD, 14));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        }

        private JTextField createDarkTextField(int columns) {
    JTextField field = new JTextField(columns) {
        @Override
        public void updateUI() {
            super.updateUI();
            // Force colors after Nimbus resets them
            setBackground(Color.BLACK);
            setForeground(Color.WHITE);
            setCaretColor(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));
        }
    };

    // Also set initially (before Nimbus loads)
    field.setBackground(Color.BLACK);
    field.setForeground(Color.WHITE);
    field.setCaretColor(Color.WHITE);
    field.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));
    return field;
}

        
        private void styleDropdown(JComboBox<String> dropdown) {
            // Editor/background style for the dropdown field (keeps it readable)
            dropdown.setBackground(Color.WHITE);
            dropdown.setForeground(Color.BLACK);
            dropdown.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150), 1));
            dropdown.setFocusable(false);
        }

        private void styleDesktopTable(JTable table) {
            table.setBackground(CARD_BACKGROUND);
            table.setForeground(TEXT_COLOR);
            table.setSelectionBackground(ACCENT_BLUE.darker());
            table.setGridColor(new Color(60, 60, 60));
            table.getTableHeader().setBackground(new Color(50, 50, 50));
            table.getTableHeader().setForeground(TEXT_COLOR);
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
            table.setRowHeight(25);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        }

        public void startNewSession(String name) {
            currentSession = new WorkoutSession();
            currentWorkoutTableModel.setRowCount(0);
            
            stopTimer();
            startTime = System.currentTimeMillis();
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::updateTimer, 0, 1, TimeUnit.SECONDS);
            
            tabbedPane.setSelectedIndex(0); 
            
            JOptionPane.showMessageDialog(this, "New Session Started: " + name, "Start", JOptionPane.INFORMATION_MESSAGE);
        }
        
        private void stopTimer() {
            if (scheduler != null) {
                scheduler.shutdownNow();
                timerLabel.setText("0:00");
            }
        }
        
        private void updateTimer() {
            long elapsed = System.currentTimeMillis() - startTime;
            long seconds = elapsed / 1000;
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            
            SwingUtilities.invokeLater(() -> {
                timerLabel.setText(String.format("%d:%02d", minutes, remainingSeconds));
            });
        }

        private void finishWorkout() {
            stopTimer();
            if (currentSession.getEntries().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Session cancelled: No exercises added.", "Cancelled", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            tracker.addSession(currentSession);
            saveProgress();
            updateHistoryAndProgressDisplays();
            
            tabbedPane.setSelectedIndex(1);
            JOptionPane.showMessageDialog(this, "Session saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
        }

        private void addEntry() {
            try {
                String selected = (String) exerciseDropdown.getSelectedItem();
                int sets = Integer.parseInt(setsField.getText());
                int reps = Integer.parseInt(repsField.getText());
                double weight = Double.parseDouble(weightField.getText());

                if (sets <= 0 || reps <= 0 || weight <= 0)
                    throw new NumberFormatException();

                String[] parts = selected.split(" \\(");
                String name = parts[0];
                String muscle = parts.length > 1 ? parts[1].replace(")", "") : "";

                StrengthExercise ex = new StrengthExercise(name, muscle, sets, reps, weight);
                WorkoutEntry entry = new WorkoutEntry(ex);
                currentSession.addEntry(entry);

                currentWorkoutTableModel.addRow(new Object[]{
                        name, sets, reps, String.format("%.1f", weight), String.format("%.1f", entry.getVolume())
                });

                setsField.setText("");
                repsField.setText("");
                weightField.setText("");

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Input must be positive numbers.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void saveProgress() {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(PROGRESS_FILE))) {
                out.writeObject(tracker);
            } catch (Exception ignored) {}
        }

        private void loadProgress() {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(PROGRESS_FILE))) {
                tracker = (ProgressTracker) in.readObject();
            } catch (Exception e) {
                tracker = new ProgressTracker();
            }
        }
        
        private void updateHistoryAndProgressDisplays() {
            StringBuilder hsb = new StringBuilder();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

            for (WorkoutSession session : tracker.getHistory()) {
                hsb.append("------------------------------------------\n");
                hsb.append("🗓️ Session on ").append(session.getDate().format(dateFormatter)).append("\n");
                
                java.util.Map<String, List<WorkoutEntry>> grouped = session.getEntries().stream()
                    .collect(Collectors.groupingBy(e -> e.getExercise().getName()));

                for(java.util.Map.Entry<String, List<WorkoutEntry>> entry : grouped.entrySet()) {
                    String exerciseName = entry.getKey();
                    int totalSets = entry.getValue().stream()
                        .mapToInt(e -> e.getExercise().getSets())
                        .sum();
                    
                    WorkoutEntry bestEntry = entry.getValue().stream()
                        .max(java.util.Comparator.comparingDouble(WorkoutEntry::getVolume))
                        .orElse(null);

                    if(bestEntry != null) {
                        hsb.append(String.format(" %d sets x %s\n  Best set: %.1f lb x %d reps\n", 
                            totalSets, exerciseName, bestEntry.getExercise().getWeight(), bestEntry.getExercise().getReps()));
                    }
                }
                hsb.append("\n");
            }
            historyTextArea.setText(hsb.toString());

            StringBuilder psb = new StringBuilder();
            psb.append("📊 Overall Statistics:\n");
            psb.append(" Total Sessions: ").append(tracker.getTotalSessions()).append("\n");
            psb.append(" Total Volume All Time: ").append(String.format("%.1f", tracker.getTotalVolumeAllTime())).append(" lb\n\n");

            psb.append("🏆 Personal Records (Estimated 1RM):\n");
            for (String ex : library.getExercises()) {
                String name = ex.split(" \\(")[0];
                double pr = tracker.getExercisePR(name);
                psb.append(String.format(" %s: %.1f lb\n", name, pr));
            }
            progressStatsArea.setText(psb.toString());
        }
    }

    // ========================================================
    // MAIN
    // ========================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GymTrackerGUI());
    }
}
