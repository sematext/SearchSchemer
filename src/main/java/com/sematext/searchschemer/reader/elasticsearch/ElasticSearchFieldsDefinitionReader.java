package com.sematext.searchschemer.reader.elasticsearch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sematext.searchschemer.index.FieldAttributes;
import com.sematext.searchschemer.index.elasticsearch.ElasticSearchFieldAttributes;
import com.sematext.searchschemer.type.elasticsearch.ElasticSearchMappingsNames;

/**
 * ElasticSearch mappings reader.
 * 
 * @author Sematext
 * 
 */
public class ElasticSearchFieldsDefinitionReader {
  /** File to parse. */
  private File file;

  /** Json reader. */
  private JsonReader reader;

  /** List of readed documents. */
  private List<FieldAttributes> fields;

  /**
   * Constructor.
   * 
   * @param file
   *          file to parse
   */
  public ElasticSearchFieldsDefinitionReader(File file) {
    this.file = file;
  }

  /**
   * Read fields from mappings file.
   * 
   * @return list of fields
   * @throws IOException
   *           thrown when I/O Error happens
   */
  public List<FieldAttributes> readFields() throws IOException {
    initialize();
    int numberOfMappings = 0;
    String typeName = null;
    reader.beginObject();
    JsonToken token = reader.peek();
    if (JsonToken.BEGIN_OBJECT == token) {
      numberOfMappings++;
      readProperties(null);
    } else if (JsonToken.NAME == token) {
      String name = reader.nextName();
      if (ElasticSearchMappingsNames.MAPPINGS.compareTo(name) == 0) {
        reader.beginObject();
        while (JsonToken.END_OBJECT != reader.peek()) {
          numberOfMappings++;
          typeName = reader.nextName();
          readProperties(typeName);
        }
        reader.endObject();
      } else {
        numberOfMappings++;
        typeName = name;
        readProperties(name);
      }

    }
    reader.endObject();
    if (numberOfMappings == 1) {
      removeTypeName(typeName);
    }
    return fields;
  }

  /**
   * Read properties.
   * 
   * @param typeName
   *          name of the type, can be <code>null</code>
   */
  private void readProperties(String typeName) throws IOException {
    reader.beginObject();
    while (true) {
      JsonToken token = reader.peek();
      if (JsonToken.NAME == token) {
        String name = reader.nextName();
        if (ElasticSearchMappingsNames.PROPERTIES.compareTo(name) == 0) {
          reader.beginObject();
          readFieldMappings(typeName);
          reader.endObject();
          break;
        } else {
          reader.skipValue();
        }
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
  }

  /**
   * Read fields.
   * 
   * @param typeName
   *          name of the type, can be <code>null</code>
   */
  private void readFieldMappings(String typeName) throws IOException {
    JsonToken token = reader.peek();
    while (JsonToken.NAME == token) {
      String name = reader.nextName();
      reader.beginObject();
      String propertyName = reader.nextName();
      String value = reader.nextString();
      if (ElasticSearchMappingsNames.MULTI_FIELD.compareTo(value) == 0) {
        handleMultiField(name, typeName);
      } else {
        ElasticSearchFieldAttributes field = new ElasticSearchFieldAttributes();
        setName(field, typeName, name);
        setProperty(propertyName, value, field);
        handleFieldAttributes(field);
        fields.add(field);
      }
      reader.endObject();
      token = reader.peek();
    }
  }

  /**
   * Handle multifield entry.
   * 
   * @param name
   *          field name
   * @param typeName
   *          name of the type
   * @throws IOException
   *           thrown when I/O error occurs
   */
  protected void handleMultiField(String name, String typeName) throws IOException {
    reader.nextName();
    reader.beginObject();
    while (JsonToken.END_OBJECT != reader.peek()) {
      ElasticSearchFieldAttributes field = new ElasticSearchFieldAttributes();
      String innerName = reader.nextName();
      if (innerName.compareTo(name) == 0) {
        setName(field, typeName, name);
      } else {
        setName(field, typeName, name + "." + innerName);
      }
      reader.beginObject();
      handleFieldAttributes(field);
      fields.add(field);
      reader.endObject();
    }
    reader.endObject();
  }

  /**
   * Handle field attributes reading.
   * 
   * @param field
   *          field
   * @throws IOException
   *           thrown when I/O error occurs
   */
  protected void handleFieldAttributes(ElasticSearchFieldAttributes field) throws IOException {
    JsonToken innerToken = reader.peek();
    while (innerToken != JsonToken.END_OBJECT) {
      if (JsonToken.NAME == reader.peek()) {
        String propertyName = reader.nextName();
        String value = reader.nextString();
        setProperty(propertyName, value, field);
      }
      innerToken = reader.peek();
    }
  }

  /**
   * Sets property for field.
   * 
   * @param propertyName
   *          property name
   * @param value
   *          property value
   * @param field
   *          field
   */
  private void setProperty(String propertyName, String value, ElasticSearchFieldAttributes field) {
    if (ElasticSearchMappingsNames.TYPE.compareTo(propertyName) == 0) {
      field.setType(value);
    } else if (ElasticSearchMappingsNames.STORE.compareTo(propertyName) == 0) {
      field.setStored(value);
    } else if (ElasticSearchMappingsNames.INDEX.compareTo(propertyName) == 0) {
      field.setAnalyzed(value);
    } else if (ElasticSearchMappingsNames.OMIT_FREQ_AND_POSITIONS.compareTo(propertyName) == 0) {
      field.setOmitTermFreqAndPositions(Boolean.parseBoolean(value));
    } else if (ElasticSearchMappingsNames.OMIT_NORMS.compareTo(propertyName) == 0) {
      field.setOmitNorms(Boolean.parseBoolean(value));
    } else if (ElasticSearchMappingsNames.BOOST.compareTo(propertyName) == 0) {
      field.setBoost(Float.parseFloat(value));
    }
  }

  /**
   * Removes type name from fields.
   * 
   * @param typeName
   *          type name
   */
  private void removeTypeName(String typeName) {
    if (typeName == null) {
      return;
    }
    int length = typeName.length();
    for (FieldAttributes field : fields) {
      if (field.name().startsWith(typeName + ".")) {
        ElasticSearchFieldAttributes currentField = (ElasticSearchFieldAttributes) field;
        currentField.setName(field.name().substring(length + 1));
      }
    }
  }
  
  /**
   * Sets field name.
   * 
   * @param field
   *          field to set the name on
   * @param typeName
   *          name of the type or <code>null</code>
   * @param name
   *          name
   */
  private void setName(ElasticSearchFieldAttributes field, String typeName, String name) {
    if (typeName == null) {
      field.setName(name);
    } else {
      field.setName(typeName + "." + name);
    }
  }

  /**
   * Initialize reader.
   * 
   * @throws FileNotFoundException
   *           thrown when clear error happens
   */
  protected void initialize() throws FileNotFoundException {
    fields = new ArrayList<FieldAttributes>();
    reader = new JsonReader(new FileReader(file));
  }
}
