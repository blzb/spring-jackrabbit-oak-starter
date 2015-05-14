package com.lucasian.repository.service.jackrabbit

import com.lucasian.repository.RepositoryItem
import com.lucasian.repository.service.RepositoryService
import com.mongodb.DB


/**
 * Created by apimentel on 5/6/15.
 */
import com.mongodb.MongoClient
import org.apache.jackrabbit.commons.cnd.CndImporter
import org.apache.jackrabbit.oak.Oak
import org.apache.jackrabbit.oak.jcr.Jcr
import org.apache.jackrabbit.oak.plugins.document.DocumentMK
import org.apache.jackrabbit.oak.plugins.document.DocumentNodeStore

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.jcr.*
import javax.jcr.nodetype.NodeType
import javax.jcr.nodetype.NodeTypeIterator
import javax.jcr.query.Query
import javax.jcr.query.QueryManager
import javax.jcr.query.QueryResult
import javax.jcr.version.Version
import javax.jcr.version.VersionIterator

class RepositoryServiceJackrabbitImpl implements RepositoryService {

  Repository repository = null
  String repositoryName = null
  boolean lazyInit = false

  @PostConstruct
  void init() {

    println("STARTING RESPOSITORY")
    try {
      DB db = new MongoClient("127.0.0.1", 27017).getDB("nsip")
      DocumentNodeStore ns = new DocumentMK.Builder().
        setMongoDB(db).getNodeStore()
      repository = new Jcr(new Oak(ns)).createRepository()

    } catch (Throwable e) {
      println(e.message)
      println("CREATING IN MEMORY REPOSITORY")
      repository = new Jcr(new Oak()).createRepository()

    }
    Session session = null
    InputStream stream = RepositoryServiceJackrabbitImpl.class.getResourceAsStream("/types.cnd")
    if(stream){
      try {
        session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()))
        stream =
          CndImporter.registerNodeTypes(
            new InputStreamReader(stream), session)
        NodeTypeIterator iterator = session.getWorkspace().getNodeTypeManager().getMixinNodeTypes()
      } finally {
        stream.close()
        if (session != null) session.logout()
      }
    }
  }


  String storeNode(RepositoryItem document) {
    Session session = null
    try {

      session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()))
      Node documentRoot = session.getRootNode()
      String[] folders = document.path.split("/")
      for (String folder : folders) {
        if (documentRoot.hasNode(folder)) {
          documentRoot = documentRoot.getNode(folder)
        } else {
          documentRoot = documentRoot.addNode(folder, "nt:folder")
        }
      }
      Node fileNode = null
      if (documentRoot.hasNode(document.name)) {
        fileNode = documentRoot.getNode(document.name)
        fileNode.checkout()
        if (document.metadata) {
          document.metadata.each { k, v ->
            fileNode.setProperty(k, v)
          }
        }
        Node contentNode = fileNode.getNode("jcr:content")
        Binary binary = session.getValueFactory().createBinary(document.binary)
        contentNode.setProperty("jcr:data", binary)
        contentNode.setProperty("jcr:mimeType", document.mimeType)
        session.save()
        fileNode.checkin()
        return fileNode.getPath()
      } else {
        fileNode = documentRoot.addNode(document.name, "nt:file")

        fileNode.addMixin("mix:versionable")
        if (document.metadata) {
          document.metadata.each { k, v ->
            println("Adding property:" + k + "=" + v)
            fileNode.setProperty(k, v)
          }
        }
        Node contentNode = fileNode.addNode("jcr:content", "nt:resource")

        Binary binary = session.getValueFactory().createBinary(document.binary)
        contentNode.setProperty("jcr:data", binary)
        contentNode.setProperty("jcr:mimeType", document.mimeType)
        session.save()
        fileNode.checkout()
        fileNode.checkin()
        session.save()
        return fileNode.getPath()
      }

    } catch (Exception e) {
      e.printStackTrace()
      throw new RuntimeException(e)
    } finally {
      if (session != null) session.logout()
    }
  }

  List<RepositoryItem> listItemsInPath(String path) {

    String queryText = "SELECT * FROM [nt:base] WHERE ISCHILDNODE([" + path + "])"
    query(queryText)
  }

  List<RepositoryItem> listFilesInPath(String path) {
    String queryText = "SELECT * FROM [nt:file] WHERE ISCHILDNODE([" + path + "])"
    query(queryText)
  }

  List<RepositoryItem> query(String queryText) {
    Session session = null
    try {
      session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()))
      def items = []

      QueryManager queryManager = session.getWorkspace().getQueryManager()
      Query query = queryManager.createQuery(queryText, "JCR-SQL2")
      QueryResult result = query.execute()
      NodeIterator it = result.getNodes()
      while (it.hasNext()) {
        Node node = it.nextNode()
        def tipos = ""
        for (NodeType tipo : node.getMixinNodeTypes()) {
          tipos += tipo.getName() + ","
        }
        if (node.getPrimaryNodeType().toString().equals("nt:file")) {
          VersionIterator i = node.getVersionHistory().getAllVersions()
          i.skip(1) // important, otherwise the currentNode will fail to read the 'title' property
          while (i.hasNext()) {
            def RepositoryItem document = new RepositoryItem(
              name: node.getName(),
              id: node.getIdentifier(),
              path: node.getPath().replace(node.getName(),""),
              itemType: tipos,
              mimeType: node.getNode("jcr:content").getProperty("jcr:mimeType").getString(),
            )
            Version v = i.nextVersion()
            NodeIterator nodeIterator = v.getNodes()
            while (nodeIterator.hasNext()) {
              def propiedades = [:]
              Node currentNode = nodeIterator.nextNode()
              for (PropertyIterator pi = currentNode.getProperties(); pi.hasNext();) {
                Property p = pi.nextProperty()
                if (!p.getName().contains(":")) {
                  int type = p.getValue().getType()
                  switch (type) {
                    case PropertyType.STRING:
                      propiedades[p.getName()] = p.getValue().getString()
                      break
                    case PropertyType.LONG:
                      propiedades[p.getName()] = p.getValue().getLong()
                      break
                    case PropertyType.DOUBLE:
                      propiedades[p.getName()] = p.getValue().getDouble()
                      break
                    case PropertyType.BOOLEAN:
                      propiedades[p.getName()] = p.getValue().getBoolean()
                      break
                    case PropertyType.DATE:
                      propiedades[p.getName()] = p.getValue().getDate()
                  }

                }
              }
              document.mimeType = currentNode.getNode("jcr:content").getProperty("jcr:mimeType").getString()
              document.version = v.getName()
              document.metadata = propiedades
              println("Documento metadata:" + propiedades)
              document.lastModified = currentNode.getNode("jcr:content").getProperty("jcr:lastModified").getDate().getTime()
              printTree(currentNode)
            }
            items << document
          }
        } else {
          def RepositoryItem document = new RepositoryItem(
            name: node.getName(),
            id: node.getIdentifier(),
            path: node.getPath(),
            mimeType: "folder",
            version: "1"
          )
          items << document
        }

      }
      return items

    } catch (Exception e) {
      e.printStackTrace()
    } finally {
      if (session != null) session.logout()
    }
  }
  Map getContent(String path){
    getVersionContent(path, null)
  }
  Map getVersionContent(String path, String version) {
    Session session = null
    try {

      String queryText = "select * from [nt:file] where [jcr:path] = '" + path + "'"
      session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()))
      def items = []
      QueryManager queryManager = session.getWorkspace().getQueryManager()
      Query query = queryManager.createQuery(queryText, "JCR-SQL2")
      QueryResult result = query.execute()
      NodeIterator it = result.getNodes()
      Node node = it.nextNode()
      Node contentNode = null
      hasPermissions(node)
      if (version != null) {
        VersionIterator i = node.getVersionHistory().getAllVersions()
        //printTree(node.getVersionHistory().getRootVersion().getNodes().nextNode())
        while (i.hasNext()) {
          Version v = i.nextVersion()
          if (v.getName().toString().equals(version)) {
            NodeIterator nodeIterator = v.getNodes()
            Node currentNode = nodeIterator.nextNode()
            contentNode = currentNode.getNode("jcr:content")
          }
        }
      } else {
        VersionIterator i = node.getVersionHistory().getAllVersions()
        Version v = null
        while (i.hasNext()) {
          v = i.nextVersion()
        }
        NodeIterator nodeIterator = v.getNodes()
        Node currentNode = nodeIterator.nextNode()

        contentNode = currentNode.getNode("jcr:content")
      }
      [
        stream: contentNode.getProperty("jcr:data").getBinary().getStream(),
        mime  : contentNode.getProperty("jcr:mimeType").getString()
      ]
    } catch (Exception e) {
      e.printStackTrace()
    } finally {
      if (session != null) session.logout()
    }
  }

  String createFolder(String path) {
    Session session = null
    try {

      session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()))
      Node documentRoot = session.getRootNode()
      String[] folders = path.split("/")
      for (String folder : folders) {
        if (documentRoot.hasNode(folder)) {
          documentRoot = documentRoot.getNode(folder)
        } else {
          documentRoot = documentRoot.addNode(folder, "nt:folder")
        }
      }
      session.save()
    } catch (Exception e) {
      e.printStackTrace()
      throw new RuntimeException(e)
    } finally {
      if (session != null) session.logout()
    }
  }

  @PreDestroy
  void shutDown() {
    try {
    } catch (Exception e) {
      e.printStackTrace()
    }
  }

  def printTree(Node node) {
    printNode(node)
    if (node.hasNodes()) {
      NodeIterator iter = node.getNodes()
      while (iter.hasNext()) {
        printTree(iter.nextNode())
      }
    }
  }

  def printNode(Node node) {
    System.out.println(node)
  }

  def printVersions(Node node) {
    try {
      VersionIterator i = node.getVersionHistory().getAllVersions()
      System.out.println("VERSIONESS:::::::::::")
      //i.skip(1) // important, otherwise the currentNode will fail to read the 'title' property
      while (i.hasNext()) {
        System.out.println("_____________________________")
        System.out.println("_____________________________")
        Version v = i.nextVersion()
        print(v.getName())
        System.out.println("_____________________________")
        System.out.println("_____________________________")
        NodeIterator nodeIterator = v.getNodes()
        while (nodeIterator.hasNext()) {
          Node currentNode = nodeIterator.nextNode()
          printTree(currentNode)
        }
      }
    } catch (Exception e) {
      System.out.println(e)
    }
  }

  def hasPermissions(Node nodo) {
    /*def logged = empleadoService.getCurrentUser()
    if(!logged.administrador){
    if(nodo.getProperty("userId").getLong() != logged.usuario.id){
    throw new AuthorizationException()
    }
    }*/
  }
}

