import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

// ========================================================
//     DESKTOP WORKOUT TRACKER (FINAL SIMPLIFIED INPUT)
// ========================================================

public class GymTracker {

    // ========================================================
    // STEP 1-5: Data Classes (StrengthExercise simplified)
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
        
        // REVISED Constructor: Uses placeholder values for calculation
        public StrengthExercise(String name, String muscleGroup) {
            super(name, muscleGroup);
            this.sets = 1; 
            this.reps = 1;
            this.weight = 100.0; // Default weight for volume/PR calculation
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
            templates.add(new WorkoutTemplate("Upper Body", new String[]{"Incline Chest Press (Chest)", "Lat Pulldown (Back)", "Shoulder Press (Shoulders)"}));
            templates.add(new WorkoutTemplate("Lower Body", new String[]{"Squat (Legs)", "Deadlift (Back)", "Leg Press (Legs)"}));
            templates.add(new WorkoutTemplate("Full Body", new String[]{"Squat (Legs)", "Bench Press (Chest)", "Deadlift (Back)"}));
        }
        public java.util.List<String> getExercises() { return exercises; }
        public java.util.List<WorkoutTemplate> getTemplates() { return templates; }
    }


    // ========================================================
    // STEP 7: GUI REVISED FOR MODERN DESKTOP VIEW
    // ========================================================
    static class GymTrackerGUI extends JFrame {

        private static final String PROGRESS_FILE = "progress.dat";
        private ExerciseLibrary library = new ExerciseLibrary();
        private ProgressTracker tracker;
        
        // Active Session Components
        private WorkoutSession currentSession;
        private DefaultTableModel currentWorkoutTableModel;
        private ScheduledExecutorService scheduler;
        private long startTime;
        private JLabel timerLabel;
        
        // UI Components
        private JTabbedPane tabbedPane;
        private JTextArea historyTextArea;
        private JTextArea progressStatsArea;
        
        // Input fields for adding exercises
        private JComboBox<String> exerciseDropdown;
        // Fields below are no longer used for input but kept as null for safety
        private JTextField setsField, repsField, weightField; 
        
        // Define standard colors
        private final Color DARK_BACKGROUND = new Color(28, 28, 30); // Primary Background
        private final Color CARD_BACKGROUND = new Color(45, 45, 50); // Card/Inner Panel Background
        private final Color TEXT_COLOR = new Color(240, 240, 240); // Light Text
        private final Color ACCENT_BLUE = new Color(50, 150, 255); // Primary Accent
        private final Color ACCENT_RED = new Color(255, 69, 58); // Danger/Finish Button

        public GymTrackerGUI() {
            loadProgress();
            currentSession = new WorkoutSession();

            setTitle("Modern Workout Tracker");
            setSize(900, 700);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            
            // Set up look and feel (Nimbus with custom colors)
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                UIManager.put("control", DARK_BACKGROUND);
                UIManager.put("nimbusBase", new Color(60, 60, 60));
                UIManager.put("nimbusBlueGrey", new Color(45, 45, 45));
                UIManager.put("text", TEXT_COLOR);
            } catch (Exception ignored) {}
            
            getContentPane().setBackground(DARK_BACKGROUND);
            
            // --- Header Title ---
            JLabel titleLabel = new JLabel("💪 Modern Workout Tracker", SwingConstants.CENTER);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
            titleLabel.setForeground(TEXT_COLOR);
            titleLabel.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
            add(titleLabel, BorderLayout.NORTH);

            // --- Tabbed Pane ---
            tabbedPane = createTabbedPane();

            tabbedPane.addTab("💪 Workout", createWorkoutScreen());
            tabbedPane.addTab("🗓️ History", createHistoryScreen());
            tabbedPane.addTab("🏆 Progress", createProgressScreen());

            add(tabbedPane, BorderLayout.CENTER);

            updateHistoryAndProgressDisplays();
            setLocationRelativeTo(null);
            setVisible(true);
        }
        
        // --- UI Component Creators ---
        
        private JTabbedPane createTabbedPane() {
            JTabbedPane tp = new JTabbedPane();
            tp.setFont(new Font("SansSerif", Font.BOLD, 14));
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
                leftPanel.add(createTemplateButton(template));
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
            button.setFont(new Font("SansSerif", Font.PLAIN, 12));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(60, 60, 60)),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            button.addActionListener(e -> startNewSession(template.getName()));
            button.setMaximumSize(new Dimension(300, 80));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            return button;
        }

        private JPanel createActiveSessionPanel() {
            JPanel rightPanel = new JPanel(new BorderLayout(15, 15));
            rightPanel.setBackground(CARD_BACKGROUND);
            rightPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            
            // --- Top Control Bar (Timer and Finish) ---
            JPanel topControl = new JPanel(new BorderLayout());
            topControl.setBackground(CARD_BACKGROUND);
            
            timerLabel = createStyledLabel("0:00", 36, true);
            topControl.add(timerLabel, BorderLayout.WEST);
            
            JButton finishButton = new JButton("FINISH SESSION");
            finishButton.setBackground(ACCENT_RED);
            finishButton.setForeground(Color.WHITE);
            finishButton.setFont(new Font("SansSerif", Font.BOLD, 14));
            finishButton.setFocusPainted(false);
            finishButton.addActionListener(e -> finishWorkout());
            topControl.add(finishButton, BorderLayout.EAST);
            
            rightPanel.add(topControl, BorderLayout.NORTH);
            
            // --- Current Session Table ---
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
            
            // --- Input Panel (Add Exercise) ---
            JPanel inputPanel = createInputPanel();
            rightPanel.add(inputPanel, BorderLayout.SOUTH);
            
            return rightPanel;
        }

        // REVISED: Only shows Exercise Dropdown and Button
        private JPanel createInputPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 5));
            panel.setBackground(CARD_BACKGROUND);
            panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)), "Add Exercise", 
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, 
                new Font("SansSerif", Font.BOLD, 14), TEXT_COLOR));
            
            JPanel fieldsPanel = new JPanel(new GridBagLayout());
            fieldsPanel.setBackground(CARD_BACKGROUND);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            
            // Only the exercise dropdown remains
            exerciseDropdown = new JComboBox<>(library.getExercises().toArray(new String[0]));
            styleDropdown(exerciseDropdown);
            
            // Row 1: Label for Exercise
            gbc.gridy = 0;
            gbc.weightx = 0.8; gbc.gridx = 0; 
            fieldsPanel.add(createStyledLabel("Exercise:", 12, false), gbc);

            // Row 2: Input Field for Exercise (takes up more space)
            gbc.gridy = 1;
            gbc.weightx = 0.8; gbc.gridx = 0; 
            fieldsPanel.add(exerciseDropdown, gbc);
            
            JButton addButton = new JButton("ADD EXERCISE"); 
            stylePrimaryButton(addButton);
            addButton.addActionListener(e -> addEntry());
            
            // Add Button (Row 2, Column 1)
            gbc.gridy = 1;
            gbc.weightx = 0.2; gbc.gridx = 1;
            fieldsPanel.add(addButton, gbc);

            panel.add(fieldsPanel, BorderLayout.CENTER);
            return panel;
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


        // --- Styling Helpers ---

        private JLabel createStyledLabel(String text, int size, boolean bold) {
            JLabel label = new JLabel(text);
            label.setForeground(TEXT_COLOR);
            int style = bold ? Font.BOLD : Font.PLAIN;
            label.setFont(new Font("SansSerif", style, size));
            return label;
        }
        
        private void stylePrimaryButton(JButton button) {
            button.setBackground(ACCENT_BLUE);
            button.setForeground(Color.WHITE);
            button.setFont(new Font("SansSerif", Font.BOLD, 14));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        }

        private JTextField createDarkTextField(int columns) {
            JTextField field = new JTextField(columns);
            field.setBackground(new Color(70, 70, 70)); // Adjusted for general visibility
            field.setForeground(Color.WHITE);
            field.setCaretColor(Color.WHITE);
            field.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));
            return field;
        }
        
        // **MODIFIED METHOD: Includes custom renderer for white background/black text in choices**
        private void styleDropdown(JComboBox<String> dropdown) {
            // Background of the JComboBox when closed
            dropdown.setBackground(new Color(70, 70, 70)); 
            dropdown.setForeground(TEXT_COLOR);
            dropdown.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));
            
            // Custom Renderer for the Popup List (the "choices")
            dropdown.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    
                    // Set default colors for choices (white background, black text)
                    label.setBackground(Color.WHITE);
                    label.setForeground(Color.BLACK);
                    
                    // Handle selection colors
                    if (isSelected) {
                        label.setBackground(new Color(180, 220, 255)); // Light Blue highlight for selection
                        label.setForeground(Color.BLACK); // Keep foreground black on selection
                    }
                    return label;
                }
            });
        }

        private void styleDesktopTable(JTable table) {
            table.setBackground(CARD_BACKGROUND);
            table.setForeground(TEXT_COLOR);
            table.setSelectionBackground(ACCENT_BLUE.darker());
            table.setGridColor(new Color(60, 60, 60));
            table.getTableHeader().setBackground(new Color(50, 50, 50));
            table.getTableHeader().setForeground(TEXT_COLOR);
            table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
            table.setRowHeight(25);
            table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        }

        // --- Session Management Logic ---
        
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
        
        private void startTimerOnly() {
            if (scheduler == null || scheduler.isShutdown()) {
                startTime = System.currentTimeMillis();
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(this::updateTimer, 0, 1, TimeUnit.SECONDS);
            }
        }
        
        private void stopTimer() {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
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
            
            currentSession = new WorkoutSession();
            currentWorkoutTableModel.setRowCount(0);
        }

        // REVISED: Only reads the exercise name
        private void addEntry() {
            try {
                String selected = (String) exerciseDropdown.getSelectedItem();
                
                String[] parts = selected.split(" \\(");
                String name = parts[0];
                String muscle = parts.length > 1 ? parts[1].replace(")", "") : "Unknown";

                // Uses the simplified constructor with default values
                StrengthExercise ex = new StrengthExercise(name, muscle);
                WorkoutEntry entry = new WorkoutEntry(ex);
                currentSession.addEntry(entry);

                // Add to the active session table (Uses the static default values for sets/reps/weight)
                currentWorkoutTableModel.addRow(new Object[]{
                        name, ex.getSets(), ex.getReps(), String.format("%.1f", ex.getWeight()), String.format("%.1f", entry.getVolume())
                });

                // Input fields are no longer cleared since only the dropdown is present
                
                startTimerOnly();

            } catch (Exception ex) {
                 JOptionPane.showMessageDialog(this, "An unexpected error occurred: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        

        // --- Data Persistence Logic ---

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
        
        // --- HISTORY AND PROGRESS DISPLAY (Simplified) ---
        
        private void updateHistoryAndProgressDisplays() {
            // HISTORY
            StringBuilder hsb = new StringBuilder();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

            for (WorkoutSession session : tracker.getHistory()) {
                hsb.append("------------------------------------------\n");
                hsb.append("🗓️ Session on ").append(session.getDate().format(dateFormatter)).append("\n");
                hsb.append("Total Volume: ").append(String.format("%.1f", session.getTotalVolume())).append("\n");
                hsb.append("------------------------------------------\n");

                // Group entries by exercise name for cleaner display
                java.util.Map<String, List<WorkoutEntry>> grouped = session.getEntries().stream()
                    .collect(Collectors.groupingBy(e -> e.getExercise().getName()));

                for (Map.Entry<String, List<WorkoutEntry>> exerciseGroup : grouped.entrySet()) {
                    String exerciseName = exerciseGroup.getKey();
                    
                    // Shows only exercise name (as requested)
                    hsb.append(" - **").append(exerciseName).append("**\n");
                } 
                hsb.append("\n");
            } 
            historyTextArea.setText(hsb.toString());

            // --- PROGRESS ---
            StringBuilder psb = new StringBuilder();
            psb.append("TOTAL WORKOUT SESSIONS: ").append(tracker.getTotalSessions()).append("\n");
            psb.append("TOTAL VOLUME LIFTED ALL TIME: ").append(String.format("%.1f", tracker.getTotalVolumeAllTime())).append("\n\n");

            // Find PRs for each exercise name
            List<String> uniqueExercises = tracker.getHistory().stream()
                    .flatMap(s -> s.getEntries().stream())
                    .map(e -> e.getExercise().getName())
                    .distinct()
                    .collect(Collectors.toList());

            psb.append("PERSONAL RECORD ESTIMATES (1RM):\n");
            for (String exerciseName : uniqueExercises) {
                double pr = tracker.getExercisePR(exerciseName);
                psb.append(" - ").append(exerciseName)
                        .append(": ").append(String.format("%.1f", pr)).append(" lbs\n");
            }

            progressStatsArea.setText(psb.toString());
        } 
        
    } // End of GymTrackerGUI class

    // ========================================================
    // STEP 8: MAIN METHOD
    // ========================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GymTrackerGUI::new);
    }
    
} // End of GymTracker class