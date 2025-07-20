## Burp Sitemap Exporter

Sitemap Exporter is an extension for Burp Suite that lets you export items from the Site Map to files on your disk. You can select specific Site Map entries using the context menu and save their HTTP responses and headers to a local folder.

### How to use the extension

![example](./assets/example.gif)

### Installation

1. Download the up-to-date BurpSitemapExporter.jar from the [Releases](https://github.com/devopscoder331/Burp-Sitemap-Exporter/releases) tab.
2. Import the BurpSitemapExporter.jar into Burp Suite.

### Manual Build

You can build the project manually using Docker:

```sh
docker run --rm -v "$PWD":/workspace -w /workspace gradle:8.7-jdk17 gradle clean jar
```

The built JAR file will be located in the `build/lib/BurpSitemapExporter.jar` directory. Then import the BurpSitemapExporter.jar into Burp Suite.