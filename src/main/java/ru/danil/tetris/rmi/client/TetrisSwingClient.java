package ru.danil.tetris.rmi.client;

import ru.danil.tetris.rmi.common.GameService;
import ru.danil.tetris.rmi.common.GameSnapshot;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public final class TetrisSwingClient {
    private static final String BINDING_NAME = "TetrisGameService";
    private static final String MENU_CARD = "menu";
    private static final String SETTINGS_CARD = "settings";
    private static final String GAME_CARD = "game";
    private static final int CELL_SIZE = 28;
    private static final int FALL_DELAY_MS = 550;
    private static final int MIN_WIDTH = 4;
    private static final int MIN_HEIGHT = 6;
    private static final int MAX_WIDTH = 25;
    private static final int MAX_HEIGHT = 25;

    private static final Color BACKGROUND = new Color(5, 14, 8);
    private static final Color PANEL_BG = new Color(8, 24, 12);
    private static final Color PANEL_BG_ALT = new Color(10, 32, 16);
    private static final Color BORDER = new Color(50, 255, 120);
    private static final Color TEXT = new Color(165, 255, 190);
    private static final Color MUTED_TEXT = new Color(105, 180, 120);
    private static final Color BOARD_BG = new Color(3, 10, 6);
    private static final Color EMPTY_CELL = new Color(9, 28, 14);
    private static final Color LOCKED_CELL = new Color(28, 180, 82);
    private static final Color ACTIVE_CELL = new Color(130, 255, 170);
    private static final Color GLOW = new Color(34, 100, 52, 120);

    private final GameService service;
    private final JFrame frame;
    private final CardLayout cardLayout;
    private final JPanel rootPanel;
    private final BoardPanel boardPanel;
    private final JLabel scoreLabel;
    private final JLabel placedLabel;
    private final JLabel holesLabel;
    private final JLabel gameOverLabel;
    private final JLabel settingsValidationLabel;
    private final JTextField widthField;
    private final JTextField heightField;
    private final JTextArea helpArea;
    private final Timer fallTimer;
    private JButton saveSettingsButton;

    private GameSnapshot snapshot;

    private TetrisSwingClient(GameService service) {
        this.service = service;
        this.frame = new JFrame("Tetris RMI");
        this.cardLayout = new CardLayout();
        this.rootPanel = new JPanel(cardLayout);
        this.boardPanel = new BoardPanel();
        this.scoreLabel = createInfoLabel();
        this.placedLabel = createInfoLabel();
        this.holesLabel = createInfoLabel();
        this.gameOverLabel = createInfoLabel();
        this.settingsValidationLabel = createInfoLabel();
        this.widthField = createTextField("10");
        this.heightField = createTextField("20");
        this.helpArea = createHelpArea();
        this.fallTimer = new Timer(FALL_DELAY_MS, this::handleTick);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String host = args.length > 0 ? args[0] : "localhost";
                int port = args.length > 1 ? Integer.parseInt(args[1]) : Registry.REGISTRY_PORT;

                Registry registry = LocateRegistry.getRegistry(host, port);
                GameService service = (GameService) registry.lookup(BINDING_NAME);

                TetrisSwingClient client = new TetrisSwingClient(service);
                client.initUi();
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(
                    null,
                    "Не удалось подключиться к серверу RMI:\n" + exception.getMessage(),
                    "Ошибка подключения",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private void initUi() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(BACKGROUND);

        rootPanel.setBackground(BACKGROUND);
        rootPanel.add(createMenuPanel(), MENU_CARD);
        rootPanel.add(createSettingsPanel(), SETTINGS_CARD);
        rootPanel.add(createGamePanel(), GAME_CARD);

        frame.add(rootPanel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        installKeyBindings();
        showCard(MENU_CARD);
    }

    private JPanel createMenuPanel() {
        JPanel panel = baseScreenPanel();

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("TETRIS RMI");
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 40));
        title.setForeground(BORDER);
        title.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("by Михальчук Д.А.");
        subtitle.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        subtitle.setForeground(MUTED_TEXT);
        subtitle.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        JLabel deco = new JLabel("01001000 01000101 01011000");
        deco.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        deco.setForeground(new Color(70, 120, 80));
        deco.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        JButton startButton = createPrimaryButton("Начать игру");
        startButton.addActionListener(event -> startGame());

        JButton settingsButton = createPrimaryButton("Изменить настройки игры");
        settingsButton.addActionListener(event -> showCard(SETTINGS_CARD));

        center.add(Box.createVerticalGlue());
        center.add(title);
        center.add(Box.createVerticalStrut(10));
        center.add(subtitle);
        center.add(Box.createVerticalStrut(10));
        center.add(deco);
        center.add(Box.createVerticalStrut(34));
        center.add(startButton);
        center.add(Box.createVerticalStrut(14));
        center.add(settingsButton);
        center.add(Box.createVerticalGlue());

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = baseScreenPanel();

        JPanel card = createCardPanel("Настройки игры");
        card.setPreferredSize(new Dimension(640, 340));
        card.setMaximumSize(new Dimension(640, 340));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel widthRow = createSettingRow("Ширина поля", widthField);
        JPanel heightRow = createSettingRow("Высота поля", heightField);

        saveSettingsButton = createPrimaryButton("Сохранить и в меню");
        saveSettingsButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        saveSettingsButton.setMaximumSize(saveSettingsButton.getPreferredSize());
        saveSettingsButton.addActionListener(event -> {
            if (validateSettingsInputs()) {
                showCard(MENU_CARD);
            }
        });

        JTextArea note = new JTextArea(
            "Меняется только размер поля. После сохранения вернитесь в меню и начните новую игру."
        );
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setForeground(MUTED_TEXT);
        note.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        note.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(28, 86, 42), 1),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        note.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        note.setMaximumSize(new Dimension(560, 76));

        settingsValidationLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        settingsValidationLabel.setForeground(new Color(255, 120, 120));
        settingsValidationLabel.setText(" ");

        content.add(widthRow);
        content.add(Box.createVerticalStrut(14));
        content.add(heightRow);
        content.add(Box.createVerticalStrut(22));
        content.add(saveSettingsButton);
        content.add(Box.createVerticalStrut(10));
        content.add(settingsValidationLabel);
        content.add(Box.createVerticalStrut(22));
        content.add(note);

        card.add(content, BorderLayout.CENTER);

        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        card.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        wrapper.add(Box.createVerticalGlue());
        wrapper.add(card);
        wrapper.add(Box.createVerticalGlue());

        panel.add(wrapper, BorderLayout.CENTER);
        installSettingsValidation();
        validateSettingsInputs();
        return panel;
    }

    private JPanel createSettingRow(String labelText, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(18, 0));
        row.setOpaque(false);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(560, 46));

        JLabel label = createFieldLabel(labelText);
        label.setPreferredSize(new Dimension(180, 46));

        field.setPreferredSize(new Dimension(220, 46));
        field.setMaximumSize(new Dimension(220, 46));

        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JPanel createGamePanel() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setPreferredSize(new Dimension(1060, 760));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        boardPanel.setBackground(BOARD_BG);
        boardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 2),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.add(createStatsPanel());
        side.add(Box.createVerticalStrut(14));
        side.add(createMenuButtonPanel());
        side.add(Box.createVerticalStrut(14));
        side.add(createHelpPanel());

        panel.add(boardPanel, BorderLayout.CENTER);
        panel.add(side, BorderLayout.EAST);
        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = createCardPanel("Состояние");
        JPanel stats = new JPanel();
        stats.setOpaque(false);
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        stats.add(scoreLabel);
        stats.add(Box.createVerticalStrut(6));
        stats.add(placedLabel);
        stats.add(Box.createVerticalStrut(6));
        stats.add(holesLabel);
        stats.add(Box.createVerticalStrut(6));
        stats.add(gameOverLabel);
        panel.add(stats, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createHelpPanel() {
        JPanel panel = createCardPanel("Клавиши");
        helpArea.setText("""
            Left / Right  - движение
            Up            - поворот
            Down          - вниз
            Space         - сбросить вниз
            Enter         - начать игру из меню
            Esc           - главное меню
            """);
        panel.add(helpArea, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createMenuButtonPanel() {
        JPanel panel = createCardPanel("Навигация");
        JButton menuButton = createPrimaryButton("Главное меню");
        menuButton.addActionListener(event -> {
            stopTimer();
            showCard(MENU_CARD);
        });
        panel.add(menuButton, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCardPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        titleLabel.setForeground(BORDER);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        panel.add(titleLabel, BorderLayout.NORTH);
        return panel;
    }

    private JPanel baseScreenPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(980, 720));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(42, 52, 42, 52));
        return panel;
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        button.setForeground(BACKGROUND);
        button.setBackground(BORDER);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 255, 190), 1),
            BorderFactory.createEmptyBorder(10, 18, 10, 18)
        ));
        button.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        return button;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        return label;
    }

    private JTextField createTextField(String value) {
        JTextField field = new JTextField(value, 5);
        field.setBackground(PANEL_BG_ALT);
        field.setForeground(TEXT);
        field.setCaretColor(BORDER);
        field.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(55, 190, 95), 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        return field;
    }

    private JTextArea createHelpArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(MUTED_TEXT);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }

    private void installSettingsValidation() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                validateSettingsInputs();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                validateSettingsInputs();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                validateSettingsInputs();
            }
        };

        widthField.getDocument().addDocumentListener(listener);
        heightField.getDocument().addDocumentListener(listener);
    }

    private boolean validateSettingsInputs() {
        String widthText = widthField.getText().trim();
        String heightText = heightField.getText().trim();
        settingsValidationLabel.setForeground(new Color(255, 120, 120));

        if (widthText.isEmpty() || heightText.isEmpty()) {
            settingsValidationLabel.setText("Введите ширину и высоту поля.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        int width;
        int height;
        try {
            width = Integer.parseInt(widthText);
            height = Integer.parseInt(heightText);
        } catch (NumberFormatException exception) {
            settingsValidationLabel.setText("Допустимы только целые числа.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            settingsValidationLabel.setText("Минимум: ширина 4, высота 6.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            settingsValidationLabel.setText("Максимум: ширина 25, высота 25.");
            saveSettingsButton.setEnabled(false);
            return false;
        }

        settingsValidationLabel.setText("Значения корректны.");
        settingsValidationLabel.setForeground(new Color(110, 220, 140));
        saveSettingsButton.setEnabled(true);
        return true;
    }

    private JLabel createInfoLabel() {
        JLabel label = new JLabel(" ");
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setForeground(TEXT);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        return label;
    }

    private void installKeyBindings() {
        bindKey("LEFT", "moveLeft", event -> executeRemote(service::moveLeft, false));
        bindKey("RIGHT", "moveRight", event -> executeRemote(service::moveRight, false));
        bindKey("UP", "rotate", event -> executeRemote(service::rotate, false));
        bindKey("DOWN", "moveDown", event -> executeRemote(service::moveDown, false));
        bindKey("SPACE", "drop", event -> executeRemote(service::dropFigure, false));
        bindKey("ENTER", "startFromMenu", event -> {
            if (isCurrentCard(MENU_CARD)) {
                startGame();
            }
        });
        bindKey("ESCAPE", "backToMenu", event -> {
            stopTimer();
            showCard(MENU_CARD);
        });
    }

    private void bindKey(String key, String actionName, java.awt.event.ActionListener listener) {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), actionName);
        root.getActionMap().put(actionName, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                listener.actionPerformed(event);
            }
        });
    }

    private void startGame() {
        int width;
        int height;
        try {
            width = Integer.parseInt(widthField.getText().trim());
            height = Integer.parseInt(heightField.getText().trim());
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(
                frame,
                "Размеры поля должны быть целыми числами.",
                "Некорректный ввод",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        if (!validateSettingsInputs()) {
            showCard(SETTINGS_CARD);
            return;
        }

        int fieldWidth = width;
        int fieldHeight = height;
        executeRemote(() -> service.startNewGame(fieldWidth, fieldHeight), true);
        if (snapshot != null) {
            showCard(GAME_CARD);
            restartTimer();
        }
    }

    private void handleTick(ActionEvent event) {
        if (!isCurrentCard(GAME_CARD) || snapshot == null || snapshot.gameOver()) {
            stopTimer();
            return;
        }
        executeRemote(service::tick, false);
    }

    private void executeRemote(RemoteAction action, boolean keepTimerRunning) {
        try {
            snapshot = action.execute();
            updateUi();
            if (snapshot.gameOver()) {
                stopTimer();
            } else if (keepTimerRunning && isCurrentCard(GAME_CARD)) {
                restartTimer();
            }
        } catch (Exception exception) {
            stopTimer();
            JOptionPane.showMessageDialog(
                frame,
                "Ошибка RMI:\n" + exception.getMessage(),
                "Ошибка",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void updateUi() {
        if (snapshot == null) {
            return;
        }

        boardPanel.setSnapshot(snapshot);
        scoreLabel.setText("Очки: " + snapshot.score() + " | Занято: " + snapshot.occupiedCells());
        placedLabel.setText("Размещено фигур: " + snapshot.placedFigures());
        holesLabel.setText("Пустоты: " + snapshot.holes());
        gameOverLabel.setText(snapshot.gameOver() ? "КОНЕЦ ИГРЫ" : " ");

        frame.pack();
        frame.repaint();
    }

    private void restartTimer() {
        fallTimer.restart();
    }

    private void stopTimer() {
        fallTimer.stop();
    }

    private void showCard(String cardName) {
        cardLayout.show(rootPanel, cardName);
        rootPanel.putClientProperty("activeCard", cardName);
    }

    private boolean isCurrentCard(String cardName) {
        Object value = rootPanel.getClientProperty("activeCard");
        return cardName.equals(value);
    }

    @FunctionalInterface
    private interface RemoteAction {
        GameSnapshot execute() throws Exception;
    }

    private static final class BoardPanel extends JPanel {
        private GameSnapshot snapshot;

        private BoardPanel() {
            setPreferredSize(new Dimension(10 * CELL_SIZE + 12, 20 * CELL_SIZE + 12));
        }

        private void setSnapshot(GameSnapshot snapshot) {
            this.snapshot = snapshot;
            setPreferredSize(new Dimension(snapshot.width() * CELL_SIZE + 12, snapshot.height() * CELL_SIZE + 12));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BOARD_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (snapshot != null) {
                List<String> rows = snapshot.boardRows();
                for (int row = 0; row < rows.size(); row++) {
                    String line = rows.get(row);
                    for (int col = 0; col < line.length(); col++) {
                        int x = 6 + col * CELL_SIZE;
                        int y = 6 + row * CELL_SIZE;
                        char cell = line.charAt(col);

                        g2.setColor(resolveGlow(cell));
                        g2.fillRoundRect(x + 2, y + 2, CELL_SIZE - 6, CELL_SIZE - 6, 10, 10);

                        g2.setColor(resolveColor(cell));
                        g2.fillRoundRect(x, y, CELL_SIZE - 4, CELL_SIZE - 4, 10, 10);

                        g2.setColor(new Color(12, 50, 22));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(x, y, CELL_SIZE - 4, CELL_SIZE - 4, 10, 10);
                    }
                }
            }

            g2.dispose();
        }

        private Color resolveColor(char cell) {
            return switch (cell) {
                case '#' -> LOCKED_CELL;
                case '*' -> ACTIVE_CELL;
                default -> EMPTY_CELL;
            };
        }

        private Color resolveGlow(char cell) {
            return switch (cell) {
                case '#', '*' -> GLOW;
                default -> new Color(0, 0, 0, 0);
            };
        }
    }
}
