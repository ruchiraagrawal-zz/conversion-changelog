
import net.steppschuh.markdowngenerator.list.UnorderedList;
import net.steppschuh.markdowngenerator.text.Text;
import net.steppschuh.markdowngenerator.text.emphasis.BoldText;
import net.steppschuh.markdowngenerator.text.heading.Heading;
import net.steppschuh.markdowngenerator.link.Link;
import okhttp3.*;

import java.io.*;
import java.util.*;

import org.json.JSONObject;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;


public class ChangelogGenerator {

    public static void main(String args[]) {

        Map<String, List<String>> headerPackageList = new HashMap<>();

//        headerPackageList.put("Syndication", Arrays.asList("src/python/web/controllers/syndication"));
//        headerPackageList.put("OLR", Arrays.asList("src/python/uc/special_feeds/olr"));
        headerPackageList.put("Conversions + Special Feeds", Arrays.asList(
                "/Users/ruchira.agrawal/development/urbancompass/src/python/uc/listing_data_sources",
                "/Users/ruchira.agrawal/development/urbancompass/src/thrift/urbancompass/listing",
                "/Users/ruchira.agrawal/development/urbancompass/src/java/com/urbancompass/converter_app",
                "/Users/ruchira.agrawal/development/urbancompass/src/python/uc/listings_conversion"
        ));


        if (args.length < 1) {
            return;
        }
        String version_old = args[0];
        String version_new = args[1];
        headerPackageList.forEach((header, packageList) -> {
            String packages = "-- " + String.join(" -- ", packageList);
            try {

                String command = String.format("git log %s..%s --decorate --stat --name-status --format=%%s*%%an*%%H %s",
                        version_old, version_new, packages);
                Process process = Runtime.getRuntime().exec(command, null, new File("/Users/ruchira.agrawal/development/urbancompass"));

                String newReleaseNotes = printMarkdown(header, version_new, process);

                Parser parser = Parser.builder().build();
                Node document = parser.parse(newReleaseNotes);
                HtmlRenderer renderer = HtmlRenderer.builder().build();
                String newReleaseNote =renderer.render(document);
                String currentReleaseNotes = getFromWiki();

                JSONObject page = new JSONObject(currentReleaseNotes);
                String currentReleaseNote =  page.getJSONObject("body").getJSONObject("storage").getString("value");
                String updatedReleaseNote = newReleaseNote + currentReleaseNote;
                page.getJSONObject("body").getJSONObject("storage").put("value", updatedReleaseNote);
                int currentVersion = page.getJSONObject("version").getInt("number");
                page.getJSONObject("version").put("number", currentVersion + 1);


                writeToWiki(page.toString());

                if (process.waitFor() == 0) {
                    System.exit(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    private static void writeToWiki(String object) throws IOException {

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, object );
        Request request = new Request.Builder()
                .url("https://compass-tech.atlassian.net/wiki/rest/api/content/1405878960")
                .method("PUT", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Basic ")
                .build();
        Response response = client.newCall(request).execute();
    }


    private static String getFromWiki() throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Request request = new Request.Builder()
                .url("https://compass-tech.atlassian.net/wiki/rest/api/content/1405878960?expand=body.storage,version").get()
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Basic ")
                .build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }


    private static String printMarkdown(String header, String version, Process process) throws IOException {
        StringBuilder sb = new StringBuilder()
//                .append(new Heading(header + "\n", 3))
                .append(new Heading(version, 3));

        String line;
        BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while((line = error.readLine()) != null) {
            System.out.println(line  + "\n");
        }
        error.close();

        BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
        int i =1;
        List<String> fileNames =null;
        while((line = input.readLine()) != null) {
            if (line != null && !line.isEmpty()) {
                if (line.startsWith("M\t") || line.startsWith("A\t") || line.startsWith("D\t")) {
                    fileNames.add(line);
                } else {
                    if(fileNames !=null) {
                        sb.append(new UnorderedList<>(fileNames));
                        sb.append("\n");
                    }
                    String[] lines = line.split("\\*");
                    sb.append(new Text("\n"));
                    sb.append(new BoldText("Message: " + lines[0]));
                    sb.append("\n");
                    sb.append(new Text( "Commited By: " + lines[1]));
                    sb.append("\n");
                    String text = lines[2];
                    String url = "https://github.com/UrbanCompass/urbancompass/commit/"+lines[2];
                    sb.append(new Link(text, url));
                    sb.append("\n");
                }
            } else {
                sb.append("\n");
                sb.append(new BoldText( "Files"));
                sb.append("\n");
                fileNames = new ArrayList<>();

            }

        }

        input.close();

        OutputStream outputStream = process.getOutputStream();
        PrintStream printStream = new PrintStream(outputStream);
        printStream.println();
        printStream.flush();
        printStream.close();

        return sb.toString();
    }

}


