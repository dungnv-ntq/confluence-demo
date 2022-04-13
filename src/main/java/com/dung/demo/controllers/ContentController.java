package com.dung.demo.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/content")
public class ContentController {
    @Autowired
    private RestTemplate restTemplate;

    private String baseUrl = "https://dungnv45.atlassian.net/wiki/rest/api/content";

    private Map<String, String> contentsMap = new HashMap<>();


    @GetMapping("/sync-content")
    public String syncContent() throws IOException {
        // clear old content
        clearOldContent();

        String getContentUrl = "https://dungnv45.atlassian.net/wiki/rest/api/content/";
        ResponseEntity<String> contentsResponse = restTemplate.getForEntity(getContentUrl, String.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode contents = mapper.readTree(contentsResponse.getBody());


        for (JsonNode content : contents.get("results")) {
            JsonNodeFactory jnf = JsonNodeFactory.instance;
            ObjectNode payload = jnf.objectNode();

            payload.set("id", content.get("id"));
            payload.set("title", content.get("title"));
            payload.set("type", content.get("type"));

            // set space
            ObjectNode space = payload.putObject("space");
            {
                space.put("id", "98414");
                space.put("name", "thangdd2");
                space.put("key", "THANGDD2");
            }

            // set body
            ObjectNode body = payload.putObject("body");

            String getBodyStorageUrl = "https://dungnv45.atlassian.net/wiki/rest/api/content/" + content.get("id").asText() + "?expand=body.storage";
            ResponseEntity<String> bodyStorageRes = restTemplate.getForEntity(getBodyStorageUrl, String.class);
            JsonNode bodyStorageNode = mapper.readTree(bodyStorageRes.getBody());

            {
                ObjectNode storage = body.putObject("storage");
                {
                    storage.put("value", bodyStorageNode.get("body").get("storage").get("value"));
                    storage.put("representation", "storage");
                }
            }

            ResponseEntity<String> responseContent = restTemplate.postForEntity("https://thangdd2.atlassian.net/wiki/rest/api/content/", payload, String.class);
            JsonNode responseContentNode = mapper.readTree(responseContent.getBody());

            contentsMap.put(content.get("id").asText(), responseContentNode.get("id").asText());

            // upload attachment
            String getAttachmentUrl = "https://dungnv45.atlassian.net/wiki/rest/api/content/" + content.get("id").asText() + "/child/attachment/";
            ResponseEntity<String> attachmentsResponse = restTemplate.getForEntity(getAttachmentUrl, String.class);

            JsonNode attachments = mapper.readTree(attachmentsResponse.getBody());
            for (JsonNode attachment : attachments.get("results")) {
                String attachmentId = attachment.get("id").asText();

                String attachmentUrl = getAttachmentUrl + attachmentId + "/download";
                String fileName = attachment.get("title").asText();

                String urlUpload = "https://thangdd2.atlassian.net/wiki/rest/api/content/"+responseContentNode.get("id").asText()+"/child/attachment";
                Resource fileUpload = getFileAttachment(attachmentUrl, fileName);
                uploadFileAttachment(urlUpload, fileUpload);
            }
        }

        // update ancestor
        updateAncestors();

        return "update success";
    }

    private void uploadFileAttachment(String urlUpload, Resource fileUpload) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Atlassian-Token", "no-check");
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileUpload);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(urlUpload, requestEntity, String.class);
    }

    private Resource getFileAttachment(String url, String fileName) throws IOException {
        byte[] imageBytes = restTemplate.getForObject(url, byte[].class);
        Files.write(Paths.get(fileName), imageBytes);

        return new FileSystemResource(Paths.get(fileName).toFile());
    }

    private void clearOldContent() throws JsonProcessingException {
        String getContentUrl = "https://thangdd2.atlassian.net/wiki/rest/api/content/";
        ResponseEntity<String> contentsResponse = restTemplate.getForEntity(getContentUrl, String.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode contents = mapper.readTree(contentsResponse.getBody());

        for (JsonNode content : contents.get("results")) {
            String contentUrl = getContentUrl + content.get("id").asText();
            restTemplate.delete(contentUrl);
        }
    }

    private void updateAncestors() throws JsonProcessingException {
        // get ancestors
        String contentUrl = "https://dungnv45.atlassian.net/wiki/rest/api/content?expand=ancestors";
        ResponseEntity<String> ancestorResponse = restTemplate.getForEntity(contentUrl, String.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode contentNodes = mapper.readTree(ancestorResponse.getBody()).get("results");

        for (JsonNode content : contentNodes) {
            JsonNode ancestors = content.get("ancestors");

            JsonNodeFactory jnf = JsonNodeFactory.instance;
            ObjectNode payload = jnf.objectNode();
            ObjectNode version = payload.putObject("version");
            {
                version.put("number", 2);
            }
            payload.set("type", content.get("type"));
            payload.set("title", content.get("title"));
            ArrayNode ancestorsPayload = payload.putArray("ancestors");

            for (JsonNode ancestor : ancestors) {
                ObjectNode ancestors0 = ancestorsPayload.addObject();
                {
                    ancestors0.put("id", contentsMap.get(ancestor.get("id").asText()));
                }
            }

            String updateContentUrl = "https://thangdd2.atlassian.net/wiki/rest/api/content/" + contentsMap.get(content.get("id").asText());
            restTemplate.put(updateContentUrl, payload);

        }
    }

}
