package net.masterthought.cucumber.json.deserializers;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.plexus.util.Base64;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import net.masterthought.cucumber.Configuration;
import net.masterthought.cucumber.ValidationException;
import net.masterthought.cucumber.json.Embedding;

/**
 * Deserializes embedding and stores it in attachment directory.
 * 
 * @author Damian Szczepanik (damianszczepanik@github)
 */
public class EmbeddingDeserializer extends CucumberJsonDeserializer<Embedding> {

    @Override
    public Embedding deserialize(JsonParser parser, Configuration configuration)
            throws IOException, JsonProcessingException {
        JsonNode rootNode = parser.getCodec().readTree(parser);
        String data = rootNode.get("data").asText();
        String mimeType = rootNode.get("mime_type").asText();

        Embedding embedding = new Embedding(mimeType, data);
        storeEmbedding(embedding, configuration);

        return embedding;
    }

    private void storeEmbedding(Embedding embedding, Configuration configuration) {
        Path file = FileSystems.getDefault().getPath(configuration.getEmbeddingDirectory().getAbsolutePath(),
                embedding.getFileId() + "." + embedding.getExtension());
        try {
            Files.write(file, Base64.decodeBase64(embedding.getData().getBytes()));
        } catch (IOException e) {
            throw new ValidationException(e);
        }
    }

}
