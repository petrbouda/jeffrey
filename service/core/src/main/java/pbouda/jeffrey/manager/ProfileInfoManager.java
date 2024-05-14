package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ArrayNode;

public interface ProfileInfoManager {

    ArrayNode events();

    byte[] information();

    void cleanup();
}
