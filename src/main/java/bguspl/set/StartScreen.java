package bguspl.set;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

/**
 * A simple dialog to setup game parameters before starting.
 */
public class StartScreen extends JDialog {
    private final Properties gameSettings;
    private boolean isConfirmed = false;

    // UI Components
    private JSpinner humanPlayersSpinner;
    private JSpinner computerPlayersSpinner;
    private JSpinner timeSpinner;

    public StartScreen() {
        setTitle("Set Card Game - Setup");
        setModal(true); // Blocks execution of Main until closed
        setSize(800, 700);
        setLocationRelativeTo(null); // Center on screen
        setLayout(new GridLayout(5, 1, 10, 10));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        gameSettings = new Properties();

        // --- Title ---
        JLabel title = new JLabel("Welcome to SET", SwingConstants.CENTER);
        title.setFont(new Font("Marker Felt", Font.BOLD, 40));
        add(title);

        // --- Settings Panel ---
        JPanel settingsPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // Human Players
        settingsPanel.add(new JLabel("Human Players:"));
        humanPlayersSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 4, 1));
        settingsPanel.add(humanPlayersSpinner);

        // Computer Players (Bots)
        settingsPanel.add(new JLabel("Computer Players:"));
        computerPlayersSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 4, 1));
        settingsPanel.add(computerPlayersSpinner);

        // Turn Time (Seconds)
        settingsPanel.add(new JLabel("Turn Time (Sec):"));
        timeSpinner = new JSpinner(new SpinnerNumberModel(60, 5, 300, 5));
        settingsPanel.add(timeSpinner);

        add(settingsPanel);

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10)); // 2 rows, 1 col
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 40, 20, 40));

        // --- Rules & Controls Button ---
        JButton rulesButton = new JButton("Rules & Controls ℹ");
        rulesButton.setFont(new Font("Marker Felt", Font.PLAIN, 14));
        rulesButton.addActionListener(e -> showRules());
        buttonPanel.add(rulesButton);

        // --- Start Button ---
        JButton startButton = new JButton("Start Game! 🧩");
        startButton.setFont(new Font("Marker Felt", Font.BOLD, 18));
        startButton.setBackground(new Color(191, 64, 191));
        startButton.setForeground(Color.WHITE);

        startButton.addActionListener(e -> {
            saveSettings();
            isConfirmed = true;
            dispose(); // Close the dialog
        });

        buttonPanel.add(startButton);

        add(buttonPanel, BorderLayout.SOUTH);
        
    }

    /**
     * Opens a popup with Game Rules and Player Controls
     */
    private void showRules() {
        // We use HTML here to format the text easily within the standard Swing message dialog
        String message = "<html><body style='width: 400px; font-family: Ariel; font-size: 12px;'>" +
                "<h2>📜 Game Rules</h2>" +
                "<ul>" +
                "<li>A <b>SET</b> consists of 3 cards.</li>" +
                "<li>For each attribute (Color, Shape, Number, Shading), the cards must be either <br>" +
                "<b>ALL THE SAME</b> or <b>ALL DIFFERENT</b>.</li>" +
                "</ul>" +
                "<hr>" +
                "<h2>⌨️ Controls</h2>" +
                "<table border='0' cellspacing='10'>" +
                "<tr>" +
                "<td valign='top'><b>Player 1 (Left):</b><br>" +
                "<font color='gray'>Grid Keys:</font><br>" +
                "Q &nbsp; W &nbsp; E &nbsp; R<br>" +
                "A &nbsp; S &nbsp; D &nbsp; F<br>" +
                "Z &nbsp; X &nbsp; C &nbsp; V</td>" +
                "<td valign='top'><b>Player 2 (Right):</b><br>" +
                "<font color='gray'>Grid Keys:</font><br>" +
                "U &nbsp; I &nbsp; O &nbsp; P<br>" +
                "J &nbsp; K &nbsp; L &nbsp; ;<br>" +
                "M &nbsp; , &nbsp; . &nbsp; /</td>" +
                "</tr>" +
                "</table>" +
                "<br><i>* Press a key to select/deselect a card slot. Select 3 cards to declare a Set.</i>" +
                "</body></html>";

        // Show the dialog
        JOptionPane.showMessageDialog(this, new JLabel(message), "How to Play", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Saves the values from the spinners into the Properties object.
     * The keys must match those expected by Config.java.
     */
    private void saveSettings() {
        gameSettings.setProperty("HumanPlayers", humanPlayersSpinner.getValue().toString());
        gameSettings.setProperty("ComputerPlayers", computerPlayersSpinner.getValue().toString());
        gameSettings.setProperty("TurnTimeoutSeconds", timeSpinner.getValue().toString());

        // Default grid size (can be added to UI later if needed)
        gameSettings.setProperty("Rows", "3");
        gameSettings.setProperty("Columns", "4");
    }

    /**
     * @return The configured properties if the user clicked Start, otherwise null.
     */
    public Properties getSettings() {
        return isConfirmed ? gameSettings : null;
    }
}