package io.dockstore.webservice.jdbi;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class EventDAO extends AbstractDAO<Event> {
    public static final int MAX_LIMIT = 100;
    public static final String PAGINATION_RANGE = "range[1,100]";
    public EventDAO(SessionFactory factory) {
        super(factory);
    }

    public Event findById(Long id) {
        return get(id);
    }

    public long create(Event event) {
        return persist(event).getId();
    }

    public long update(Event event) {
        return persist(event).getId();
    }

    public List<Event> findEventsForOrganization(long organizationId, Integer offset, Integer limit) {
        Query query = namedQuery("io.dockstore.webservice.core.Event.findAllForOrganization")
                .setParameter("organizationId", organizationId)
                .setFirstResult(offset)
                .setMaxResults(limit);
        return list(query);
    }

    public long countAllEventsForOrganization(long organizationId) {
        final Query query = namedQuery("io.dockstore.webservice.core.Event.countAllForOrganization")
                .setParameter("organizationId", organizationId);
        return ((Long)query.getSingleResult()).longValue();
    }

    public List<Event> findEventsByEntryIDs(Set<Long> entryIds, Integer offset, int limit) {
        int newLimit = Math.min(MAX_LIMIT, limit);
        if (entryIds.isEmpty()) {
            return Collections.emptyList();
        }
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.findAllByEntryIds");
        query.setParameterList("entryIDs", entryIds).setFirstResult(offset).setMaxResults(newLimit);
        return list(query);
    }

    public List<Event> findAllByOrganizationIds(Set<Long> organizationIds, Integer offset, int limit) {
        int newLimit = Math.min(MAX_LIMIT, limit);
        if (organizationIds.isEmpty()) {
            return Collections.emptyList();
        }
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.findAllByOrganizationIds");
        query.setParameterList("organizationIDs", organizationIds).setFirstResult(offset).setMaxResults(newLimit);
        return list(query);
    }

    public List<Event> findAllByOrganizationIdsOrEntryIds(Set<Long> organizationIds, Set<Long> entryIds, Integer offset, int limit) {
        int newLimit = Math.min(MAX_LIMIT, limit);
        if (organizationIds.isEmpty()) {
            return Collections.emptyList();
        }
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.findAllByOrganizationIdsOrEntryIds");
        query.setParameterList("organizationIDs", organizationIds).setParameter("entryIDs", entryIds)
                .setFirstResult(offset).setMaxResults(newLimit);
        return list(query);
    }

    public void delete(Event event) {
        Session session = currentSession();
        session.delete(event);
        session.flush();
    }

    public void deleteEventByEntryID(long entryId) {
        currentSession().flush();
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.deleteByEntryId");
        query.setParameter("entryId", entryId);
        query.executeUpdate();
        currentSession().flush();
    }

    public void createAddTagToEntryEvent(User user, Entry entry, Version version) {
        if (version.getReferenceType() == Version.ReferenceType.TAG) {
            Event event = entry.getEventBuilder().withType(Event.EventType.ADD_VERSION_TO_ENTRY).withInitiatorUser(user).withVersion(version).build();
            create(event);
        }
    }
}
