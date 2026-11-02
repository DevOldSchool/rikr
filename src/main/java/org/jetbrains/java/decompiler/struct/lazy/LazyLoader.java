// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Rikr: a copy of FernFlower's LazyLoader, patched where marked "Rikr:" so loading a method's code doesn't read its
// class again up to the method. The build puts it in place of the original; see docs/decisions/0012 and
// patches/fernflower.
package org.jetbrains.java.decompiler.struct.lazy;

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LazyLoader {
  private final Map<String, Link> mapClassLinks = new HashMap<>();
  private final IBytecodeProvider provider;
  // Rikr: for each class, where each method's code starts in the class file, by the
  // method's name and descriptor. Found in one pass the first time a method's code is loaded: reading the class up to
  // the method each time, constant pool included, took a tenth of the time on classes with hundreds of methods.
  // Concurrent, since a class's methods are decompiled in parallel.
  private final Map<String, Map<String, Integer>> codeOffsets = new ConcurrentHashMap<>();

  public LazyLoader(IBytecodeProvider provider) {
    this.provider = provider;
  }

  public void addClassLink(String className, Link link) {
    mapClassLinks.put(className, link);
    codeOffsets.remove(className); // Rikr
  }

  public void removeClassLink(String className) {
    mapClassLinks.remove(className);
    codeOffsets.remove(className); // Rikr
  }

  public Link getClassLink(String className) {
    return mapClassLinks.get(className);
  }

  public ConstantPool loadPool(String className) {
    try (DataInputFullStream in = getClassStream(className)) {
      if (in != null) {
        in.discard(8);
        return new ConstantPool(in);
      }

      return null;
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public byte[] loadBytecode(StructClass classStruct, StructMethod mt, int codeFullLength) {
    // Rikr: reads the code from where findCode found it.
    Integer offset = findCode(classStruct, mt);
    if (offset == null) {
      return null;
    }

    try (DataInputFullStream in = getClassStream(classStruct.qualifiedName)) {
      if (in != null) {
        in.discard(offset);
        return in.read(codeFullLength);
      }

      return null;
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  // Rikr: where the method's code starts in its class file, or null.
  private Integer findCode(StructClass classStruct, StructMethod mt) {
    Map<String, Integer> offsets = codeOffsets.get(classStruct.qualifiedName);
    if (offsets == null) {
      offsets = readCodeOffsets(classStruct);
      if (offsets == null) {
        return null;
      }
      codeOffsets.putIfAbsent(classStruct.qualifiedName, offsets);
    }
    return offsets.get(InterpreterUtil.makeUniqueKey(mt.getName(), mt.getDescriptor()));
  }

  // Rikr: the class file read as loadBytecode read it for one method, for all of them.
  private Map<String, Integer> readCodeOffsets(StructClass classStruct) {
    String className = classStruct.qualifiedName;

    try (DataInputFullStream in = getClassStream(className)) {
      if (in != null) {
        int length = in.available();
        in.discard(8);

        ConstantPool pool = classStruct.getPool();
        if (pool == null) {
          pool = new ConstantPool(in);
        }
        else {
          ConstantPool.skipPool(in);
        }

        in.discard(6);

        // interfaces
        in.discard(in.readUnsignedShort() * 2);

        // fields
        int size = in.readUnsignedShort();
        for (int i = 0; i < size; i++) {
          in.discard(6);
          skipAttributes(in);
        }

        // methods
        Map<String, Integer> offsets = new HashMap<>();
        size = in.readUnsignedShort();
        for (int i = 0; i < size; i++) {
          in.discard(2);

          int nameIndex = in.readUnsignedShort();
          int descriptorIndex = in.readUnsignedShort();

          String[] values = pool.getClassElement(ConstantPool.METHOD, className, nameIndex, descriptorIndex);
          String key = InterpreterUtil.makeUniqueKey(values[0], values[1]);

          int attrSize = in.readUnsignedShort();
          for (int j = 0; j < attrSize; j++) {
            int attrNameIndex = in.readUnsignedShort();
            String attrName = pool.getPrimitiveConstant(attrNameIndex).getString();
            int attrLength = in.readInt();
            if (StructGeneralAttribute.ATTRIBUTE_CODE.name.equals(attrName)) {
              // The code follows max_stack, max_locals and code_length; the first method of a name and
              // descriptor wins, as it did.
              offsets.putIfAbsent(key, length - in.available() + 8);
            }
            in.discard(attrLength);
          }
        }
        return offsets;
      }

      return null;
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public DataInputFullStream getClassStream(String externalPath, String internalPath) throws IOException {
    byte[] bytes = provider.getBytecode(externalPath, internalPath);
    return new DataInputFullStream(bytes);
  }

  public DataInputFullStream getClassStream(String qualifiedClassName) throws IOException {
    Link link = mapClassLinks.get(qualifiedClassName);
    return link == null ? null : link.data != null ? new DataInputFullStream(link.data) : getClassStream(link.externalPath, link.internalPath);
  }

  public static void skipAttributes(DataInputFullStream in) throws IOException {
    int length = in.readUnsignedShort();
    for (int i = 0; i < length; i++) {
      in.discard(2);
      in.discard(in.readInt());
    }
  }

  public static class Link {
    public final String externalPath;
    public final String internalPath;
    public final byte[] data;

    public Link(String externalPath, String internalPath) {
        this(externalPath, internalPath, null);
    }

    public Link(String externalPath, String internalPath, byte[] data) {
      this.externalPath = externalPath;
      this.internalPath = internalPath;
      this.data = data;
    }
  }
}
