package org.gu.fms.desktop.platform.ui.nav;

import java.util.*;

public final class RouteRegistry {
    private final List<Route> routes;

    public RouteRegistry(List<Route> routes) {
        Map<String, Route> byId = new LinkedHashMap<>();
        for (Route route : routes) {
            if (byId.put(route.id(), route) != null) {
                throw new IllegalArgumentException("Route en double : " + route.id());
            }
        }
        this.routes = routes;
    }

    public List<Route> all() {
        return routes;
    }

    public Optional<Route> find(String id) {
        return routes.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public Route get(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Route inconnue : " + id));
    }

    public Map<String, List<Route>> sections() {
        Map<String, List<Route>> sections = new LinkedHashMap<>();
        for (Route route : routes) {
            if (!Route.FOOTER.equals(route.sectionKey())) {
                sections.computeIfAbsent(route.sectionKey(), k -> new ArrayList<>()).add(route);
            }
        }
        sections.replaceAll((k, v) -> Collections.unmodifiableList(v));
        return Collections.unmodifiableMap(sections);
    }
}
