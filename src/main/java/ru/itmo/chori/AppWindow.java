package ru.itmo.chori;

import javax.swing.*;

public class AppWindow {
    private JTextField vckpgPath;
    private JButton vcpkgFileChooser;
    private JLabel statusMessage;
    private JButton buttonTest;
    private JPanel contentPane;
    private JLabel labelVcpkgPath;
    private JButton refreshButton;

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
}
