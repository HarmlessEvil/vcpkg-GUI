package ru.itmo.chori;

import javax.swing.*;
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

    private final AtomicInteger foundPackagesCount = new AtomicInteger(0);
    private final AtomicBoolean isInstalling = new AtomicBoolean(false);

    public int addAndGetFoundPackagesCount(int delta) {
        int oldValue = foundPackagesCount.getAndAdd(delta);
        int newValue = oldValue + delta;

        pcs.firePropertyChange("found", oldValue, newValue);

        return newValue;
    }

    public boolean isInstalling() {
        return isInstalling.get();
    }

    public void setInstalling(boolean newValue) {
        boolean oldValue = isInstalling.getAndSet(newValue);

        pcs.firePropertyChange("installing", oldValue, newValue);
    }

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public AddPackageDialog() {
        setContentPane(contentPane);
        setModal(true);

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(
                e -> onCancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );
    }

    private void onCancel() {
        if (isInstalling()) {
            return;
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
