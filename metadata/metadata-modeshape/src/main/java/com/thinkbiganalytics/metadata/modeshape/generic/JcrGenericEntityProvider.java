/**
 * 
 */
package com.thinkbiganalytics.metadata.modeshape.generic;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;
import javax.jcr.nodetype.PropertyDefinitionTemplate;

import com.thinkbiganalytics.metadata.api.generic.GenericEntity;
import com.thinkbiganalytics.metadata.api.generic.GenericEntity.ID;
import com.thinkbiganalytics.metadata.api.generic.GenericEntityProvider;
import com.thinkbiganalytics.metadata.api.generic.GenericType;
import com.thinkbiganalytics.metadata.modeshape.JcrMetadataAccess;
import com.thinkbiganalytics.metadata.modeshape.JcrUtil;
import com.thinkbiganalytics.metadata.modeshape.MetadataRepositoryException;

/**
 *
 * @author Sean Felten
 */
public class JcrGenericEntityProvider implements GenericEntityProvider {

    // default/entities
    
    /**
     * 
     */
    public JcrGenericEntityProvider() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public GenericType createType(String name, Map<String, GenericType.PropertyType> props) {
        return createType(name, null, props);
    }

    @Override
    @SuppressWarnings("unchecked")
    public GenericType createType(String name, GenericEntity supertype, Map<String, GenericType.PropertyType> props) {
        try {
            Session session = getSession();
            NodeTypeManager typeMgr = session.getWorkspace().getNodeTypeManager();
            NodeTypeTemplate nodeTemplate = typeMgr.createNodeTypeTemplate();
            nodeTemplate.setName(JcrMetadataAccess.META_PREFIX + ":" + name);
            
            if (supertype != null) {
                String supername = supertype.getTypeName();
                nodeTemplate.setDeclaredSuperTypeNames(new String[] { supername });
            }
            
            for (Entry<String, GenericType.PropertyType> entry : props.entrySet()) {
                PropertyDefinitionTemplate propDef = typeMgr.createPropertyDefinitionTemplate();
                propDef.setName(entry.getKey());
                propDef.setRequiredType(JcrUtil.asCode(entry.getValue()));
                nodeTemplate.getPropertyDefinitionTemplates().add(propDef);
            }
            
            NodeType nodeType = typeMgr.registerNodeType(nodeTemplate, true);
            
            if (! session.nodeExists("/generic/" + name)) {
                session.getRootNode().addNode("generic/" + name);
            }
            
            return new JcrGenericType(nodeType);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to create new generic type: " + name, e);
        }
    }
    
    @Override
    public GenericType getType(String name) {
        Session session = getSession();
        try {
            NodeTypeManager typeMgr = session.getWorkspace().getNodeTypeManager();
            NodeType nodeType = typeMgr.getNodeType(JcrMetadataAccess.META_PREFIX + ":" + name);
            return new JcrGenericType(nodeType);
        } catch (NoSuchNodeTypeException e) {
            return null;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to lookup generic type: " + name, e);
        }
    }
    
    @Override
    public List<GenericType> getTypes() {
        Session session = getSession();
        try {
            List<GenericType> list = new ArrayList<GenericType>();
            NodeTypeManager typeMgr = session.getWorkspace().getNodeTypeManager();
            NodeTypeIterator typeItr = typeMgr.getPrimaryNodeTypes();
            
            while (typeItr.hasNext()) {
                NodeType nodeType = (NodeType) typeItr.next();
                
                if (nodeType.getName().startsWith(JcrMetadataAccess.META_PREFIX)) {
                    list.add(new JcrGenericType(nodeType));
                }
            }
            
            return list;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to lookup all generic types", e);
        }
    }
    
    @Override
    public GenericEntity createEntity(GenericType type, Map<String, Object> props) {
        JcrGenericType typeImpl = (JcrGenericType) type;
        Session session = getSession();
        
        try {
            Node typesNode = session.getNode(Paths.get("/generic", typeImpl.getName()).toString());
            Node entNode = typesNode.addNode(UUID.randomUUID().toString(), typeImpl.getJcrName());
            entNode = JcrUtil.setProperties(session, entNode, props);
            
            return new JcrGenericEntity(entNode);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to create new generic entity of type: " + typeImpl.getName(), e);
        }
    }

    @Override
    public GenericEntity getEntity(ID id) {
        JcrGenericEntity.EntityId idImpl = (JcrGenericEntity.EntityId) id;
        
        try {
            Session session = getSession();
            Node node = session.getNodeByIdentifier(idImpl.getIdValue());
            
            if (node != null) {
                return new JcrGenericEntity(node);
            } else {
                return null;
            }
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failure while finding entity by ID: " + idImpl.getIdValue(), e);
        }
    }
    
    @Override
    public List<GenericEntity> getEntities() {
        List<GenericEntity> list = new ArrayList<>();
        Session session = getSession();
        
        try {
            Node genericsNode = session.getNode("/generic");
            NodeIterator typeNameItr = genericsNode.getNodes();
            
            while (typeNameItr.hasNext()) {
                Node typeNameNode = (Node) typeNameItr.next();
                NodeIterator entityItr = typeNameNode.getNodes();
                
                while (entityItr.hasNext()) {
                    Node entNode = (Node) entityItr.next();
                    list.add(new JcrGenericEntity(entNode));
                }
            }
            
            return list;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to retrieve list of generic entities", e);
        }
    }

    private Session getSession() {
        return JcrMetadataAccess.getActiveSession();
    }

}