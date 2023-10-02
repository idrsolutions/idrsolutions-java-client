/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **/
package idrsolutions;


import java.util.HashMap;
import java.util.Map;

public final class ExampleUsage {

    public static void main(final String[] args) {

        // Append BUILDVU, JPEDAL or FORMVU in the URL depending on which microservice you're using
        final IDRCloudClient client = new IDRCloudClient("https://example/" + IDRCloudClient.BUILDVU);

        
        // Options to convert from a file upload:
        // Add to 'params' with API values that are sent to the server. See
        // https://github.com/idrsolutions/buildvu-microservice-example/blob/master/API.md  for more details on buildvu
        // https://github.com/idrsolutions/jpedal-microservice-example/blob/master/API.md for more details on jpedal
        // https://github.com/idrsolutions/formvu-microservice-example/blob/master/API.md for more details on formvu
        final HashMap<String, String> params = new HashMap<>();
        params.put("token", "token");
        params.put("input", IDRCloudClient.UPLOAD); //required
        params.put("file", "path/to/file.pdf"); //required

        // Options to convert from a url download:
        // Alternatively you can get the server to download the pdf file directly
        // by changing input and file to these:
        //params.put("input", IDRCloudClient.DOWNLOAD);
        //params.put("url", "http://path.to/file.pdf");

        try {
            final Map<String, String> results = client.convert(params);

            System.out.println("   ---------   ");
            System.out.println(results.get("previewUrl"));

            IDRCloudClient.downloadResults(results, "path/to/outputDir", "example");
        } catch (final ClientException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}