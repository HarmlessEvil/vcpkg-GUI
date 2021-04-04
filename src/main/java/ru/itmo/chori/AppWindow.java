package ru.itmo.chori;

import ru.itmo.chori.models.PackagesTableModel;

import javax.swing.*;
import java.util.Collections;

public class AppWindow {
    private JTextField vckpgPath;
    private JButton vcpkgFileChooser;
    private JLabel statusMessage;
    private JButton buttonTest;
    private JPanel contentPane;
    private JLabel labelVcpkgPath;
    private JButton refreshButton;
    private JTable tablePackages;
    private JTextArea textAreaStatus;
    private JButton buttonAdd;
    private JButton buttonRemove;

    public JButton getVcpkgFileChooser() {
        return vcpkgFileChooser;
    }

    public JPanel getContentPane() {
        return contentPane;
    }

    public JTextField getVckpgPath() {
        return vckpgPath;
    }

    public JButton getButtonTest() {
        return buttonTest;
    }

    public JLabel getStatusMessage() {
        return statusMessage;
    }

    public JTable getTablePackages() {
        return tablePackages;
    }

    public JButton getRefreshButton() {
        return refreshButton;
    }

    public JTextArea getTextAreaStatus() {
        return textAreaStatus;
    }

    public JButton getButtonAdd() {
        return buttonAdd;
    }

    public JButton getButtonRemove() {
        return buttonRemove;
    }

    private void createUIComponents() {
        tablePackages = new JTable(new PackagesTableModel(Collections.emptyList()));
        tablePackages.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
    }
}
