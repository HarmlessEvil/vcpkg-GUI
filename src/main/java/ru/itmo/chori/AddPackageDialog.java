package ru.itmo.chori;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AddPackageDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonClose;
    private JComboBox<String> comboBoxPackage;
    private JButton buttonSearch;
    private JButton buttonInstall;
    private JTextArea textAreaInstallStatus;
    private JLabel labelFound;
    private JProgressBar progressBarSearch;
    private JScrollPane scrollPane;

    private final AtomicInteger foundPackagesCount = new AtomicInteger(0);
    private final AtomicBoolean isInstalling = new AtomicBoolean(false);

    public int addAndGetFoundPackagesCount(int delta) {
        int oldValue = foundPackagesCount.getAndAdd(delta);
        int newValue = oldValue + delta;

        pcs.firePropertyChange("found", oldValue, newValue);

        return newValue;
    }

    public void setFoundPackagesCount(int count) {
        int oldValue = foundPackagesCount.getAndSet(count);

        pcs.firePropertyChange("found", oldValue, count);
    }

    public boolean isInstalling() {
        return isInstalling.get();
    }

    public void setInstalling(boolean newValue) {
        isInstalling.set(newValue);
    }

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    private Runnable onCloseActionListener;

    public void setOnClose(Runnable onCloseActionListener) {
        this.onCloseActionListener = onCloseActionListener;
    }

    public AddPackageDialog() {
        setContentPane(contentPane);
        setModal(true);

        buttonClose.addActionListener(e -> onClose());

        // call onClose() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });

        // call onClose() on ESCAPE
        contentPane.registerKeyboardAction(
                e -> onClose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );

        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        textAreaInstallStatus.setOpaque(false);

        comboBoxPackage.setPrototypeDisplayValue("sqlite3[sqlite3]");
    }

    private void onClose() {
        if (isInstalling()) {
            return;
        }

        if (onCloseActionListener != null) {
            onCloseActionListener.run();
        }

        dispose();
    }

    public JButton getButtonSearch() {
        return buttonSearch;
    }

    public JButton getButtonInstall() {
        return buttonInstall;
    }

    public JComboBox<String> getComboBoxPackage() {
        return comboBoxPackage;
    }

    public JProgressBar getProgressBarSearch() {
        return progressBarSearch;
    }

    public JLabel getLabelFound() {
        return labelFound;
    }

    public JTextArea getTextAreaInstallStatus() {
        return textAreaInstallStatus;
    }
}
