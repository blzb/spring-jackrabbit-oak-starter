package com.lucasian.repository

/**
 * Created by apimentel on 5/6/15.
 */
class RepositoryItem {
  String name
  String path
  String itemType
  String id
  Map metadata
  String mimeType
  String version
  InputStream binary
  Long userId
  Date lastModified
  String toString(){
    "name["+name+"] path["+path+"] itemType["+itemType+"] id["+id+"] metadata["+metadata+"] mimeType["+mimeType+"]"
  }

}
