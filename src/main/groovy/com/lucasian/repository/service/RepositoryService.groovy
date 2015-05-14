package com.lucasian.repository.service

import com.lucasian.repository.RepositoryItem

/**
 * Created by apimentel on 5/6/15.
 */
interface RepositoryService{
  String storeNode(RepositoryItem documento)
  List<RepositoryItem> listItemsInPath(String path)
  List<RepositoryItem> listFilesInPath(String path)
  Map getVersionContent(String path, String version)
  Map getContent(String path)
  List<RepositoryItem> query(String queryText)
  String createFolder(String path)
}