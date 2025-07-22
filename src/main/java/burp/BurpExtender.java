package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BurpExtender implements BurpExtension, ContextMenuItemsProvider {
    private MontoyaApi api;
    private JPanel panel;
    private JTextArea logArea;
    private boolean tabRegistered = false;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("SiteMap Exporter");

        panel = new JPanel(new BorderLayout());
        JButton exportButton = new JButton("Export Site Map");
        logArea = new JTextArea(20, 60);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        panel.add(exportButton, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        // api.userInterface().registerSuiteTab("Site Map Exporter", panel);
        api.userInterface().registerContextMenuItemsProvider(this);

        exportButton.addActionListener(e -> {
            logArea.append("Export button clicked: exporting entire Site Map\n");
            List<HttpRequestResponse> siteMap = api.siteMap().requestResponses();
            exportHttpMessages(siteMap);
        });

        logArea.append("Extension loaded. Use the context menu in Target->Site Map.\n");
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (!event.invocationType().toString().contains("SITE_MAP")) {
            return null;
        }
        List<HttpRequestResponse> selectedItems = event.selectedRequestResponses();
        int count = selectedItems == null ? 0 : selectedItems.size();
        logArea.append("Context menu invoked, selected messages count = " + count + "\n");
        if (count == 0) {
            logArea.append("No selected items, no menu added.\n");
            return null;
        }
        JMenuItem exportSelected = new JMenuItem("Export Selected Items");
        exportSelected.addActionListener(e -> {
            if (!tabRegistered) {
                api.userInterface().registerSuiteTab("SiteMap Exporter", panel);
                tabRegistered = true;
            }
            logArea.append("Export Selected Items clicked: exporting " + count + " items\n");
            List<HttpRequestResponse> allSiteMapItems = api.siteMap().requestResponses();
            java.util.Set<HttpRequestResponse> itemsToExport = new java.util.HashSet<>();
            for (HttpRequestResponse selected : selectedItems) {
                String selectedUrl = selected.request().url();
                itemsToExport.add(selected);
                for (HttpRequestResponse candidate : allSiteMapItems) {
                    String candidateUrl = candidate.request().url();
                    if (!candidate.equals(selected) && candidateUrl.startsWith(selectedUrl)) {
                        itemsToExport.add(candidate);
                    }
                }
            }
            List<HttpRequestResponse> itemsWithResponse = new ArrayList<>();
            for (HttpRequestResponse item : itemsToExport) {
                HttpResponse resp = item.response();
                try {
                    if (resp != null && resp.body().length() > 0) {
                        itemsWithResponse.add(item);
                    } else {
                        String urlStr = item.request().url();
                        logArea.append("Skipping item with empty response: " + urlStr + "\n");
                    }
                } catch (Exception ex) {
                    logArea.append("Skipping item with empty response (URL unavailable)\n");
                }
            }
            if (itemsWithResponse.isEmpty()) {
                logArea.append("No selected items have non-empty responses. Nothing to export.\n");
                return;
            }
            exportHttpMessages(itemsWithResponse);
        });
        return List.of(exportSelected);
    }

    private File buildFilePath(File outputDir, URL url) {
        String host = url.getHost();
        String path = url.getPath();
        if (path.isEmpty() || path.endsWith("/")) {
            path += "index.html";
        }
        path = path.replaceAll("([\\:*?\"<>|])", "_");
        if (path.startsWith("/")) path = path.substring(1);
        File filePath = new File(outputDir, host + File.separator + path);
        filePath.getParentFile().mkdirs();
        return filePath;
    }

    private void exportHttpMessages(List<HttpRequestResponse> messages) {
        exportHttpMessages(messages, true);
    }

    private void exportHttpMessages(List<HttpRequestResponse> messages, boolean saveHeaders) {
        if (messages == null || messages.isEmpty()) {
            logArea.append("Nothing to export.\n");
            return;
        }

        // Create a panel with a checkbox for saving headers and overwrite existing files
        JCheckBox headersCheckBox = new JCheckBox("Save files with headers", true);
        JCheckBox overwriteCheckBox = new JCheckBox("Overwrite existing files", false);
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        JPanel accessory = new JPanel();
        accessory.setLayout(new BoxLayout(accessory, BoxLayout.Y_AXIS));
        accessory.add(headersCheckBox);
        accessory.add(overwriteCheckBox);
        chooser.setAccessory(accessory);

        int res = chooser.showSaveDialog(panel);
        if (res != JFileChooser.APPROVE_OPTION) {
            logArea.append("Export cancelled by user.\n");
            return;
        }

        File outputDir = chooser.getSelectedFile();
        logArea.append("Exporting to folder: " + outputDir.getAbsolutePath() + "\n");

        boolean shouldSaveHeaders = headersCheckBox.isSelected();
        boolean shouldOverwrite = overwriteCheckBox.isSelected();
        int savedCount = 0;

        for (HttpRequestResponse entry : messages) {
            try {
                HttpResponse response = entry.response();
                if (response == null || response.body().length() == 0) {
                    String urlStr = entry.request().url();
                    logArea.append("Skipping (empty response): " + urlStr + "\n");
                    continue;
                }

                String urlStr = entry.request().url();
                URL url = new URL(urlStr);
                File filePath = buildFilePath(outputDir, url);
                filePath.getParentFile().mkdirs();

                if (filePath.exists() && !shouldOverwrite) {
                    logArea.append("File exists, skipping (overwrite disabled): " + filePath.getAbsolutePath() + "\n");
                    continue;
                }

                try (FileOutputStream fos = new FileOutputStream(filePath)) {
                    fos.write(response.body().getBytes());
                }

                if (shouldSaveHeaders) {
                    File headersPath = new File(filePath.getParentFile(), filePath.getName() + "_headers.txt");
                    if (!headersPath.exists() || shouldOverwrite) {
                        try (PrintWriter writer = new PrintWriter(headersPath)) {
                            for (HttpHeader header : response.headers()) {
                                writer.println(header.toString());
                            }
                        }
                    } else {
                        logArea.append("Headers file exists, skipping (overwrite disabled): " + headersPath.getAbsolutePath() + "\n");
                    }
                }

                logArea.append("Saved: " + urlStr + "\n");
                savedCount++;
            } catch (Exception ex) {
                try {
                    String urlStr = entry.request().url();
                    logArea.append("Error saving " + urlStr + ": " + ex.getMessage() + "\n");
                } catch (Exception ex2) {
                    logArea.append("Error saving (URL unavailable): " + ex.getMessage() + "\n");
                }
            }
        }

        logArea.append("Export complete. Files saved: " + savedCount + "\n");
    }
} 