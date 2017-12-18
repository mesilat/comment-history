package com.mesilat.comments;

import com.atlassian.activeobjects.spi.DataSourceProvider;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.pages.Comment;
import com.atlassian.confluence.pages.CommentManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.user.User;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
@Scanned
public class CommentHistoryResource {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.comments-history");

    private final LocaleManager localeManager;
    private final UserManager userManager;
    private final UserAccessor userAccessor;
    private final DataSourceProvider dataSourceProvider;
    private final CommentManager commentManager;
    private final ObjectMapper mapper = new ObjectMapper();

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response get(@Context HttpServletRequest request) {
        String commentId = request.getParameter("commentId");

        if (commentId == null) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity("Please specify comment id")
                .build();
        }

        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null) {
            return Response
                .status(Response.Status.UNAUTHORIZED)
                .entity("Authentication required")
                .build();
        }

        List<Long> history;
        try {
            history = getCommentHistory(Long.parseLong(commentId));
        } catch (SQLException ex) {
            LOGGER.warn("Failed to get comment history", ex);
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ex.getMessage())
                .build();
        }

        User user = userAccessor.getUserByKey(userKey);
        Locale locale = localeManager.getLocale(user);
        DateTimeFormatter formatter = DateTimeFormat.forPattern(DateTimeFormat.patternForStyle("MS", locale));

        ArrayNode arr = mapper.createArrayNode();
        Comment comment = commentManager.getComment(Long.parseLong(commentId));
        arr.add(getObjectNode(comment, formatter));
        
        if (history != null) {
            for (Long id : history) {
                comment = commentManager.getComment(id);
                arr.add(getObjectNode(comment, formatter));
            }
        }
        return Response.ok(arr).build();
    }
    private String formatDate(Date date, DateTimeFormatter formatter) {
        StringWriter sw = new StringWriter();
        try {
            formatter.printTo(sw, date.getTime());
            return sw.toString();
        } catch (IOException ex) {
            return ex.getMessage();
        }
    }
    private List<Long> getCommentHistory(long contentId) throws SQLException {
        List<Long> comments = new ArrayList<>();
        try (Connection conn = dataSourceProvider.getDataSource().getConnection()) {

            String sql = dataSourceProvider.getSchema() == null?
                    "select contentid from content where prevver=? order by lastmoddate desc":
                    String.format("select contentid from %s.content where prevver=? order by lastmoddate desc", dataSourceProvider.getSchema());
            try (PreparedStatement stmt = conn.prepareStatement(sql.toUpperCase())) {
                stmt.setLong(1, contentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        comments.add(rs.getLong("contentid"));
                    }
                }
            }
        }
        return comments;
    }
    private ObjectNode getObjectNode(Comment comment, DateTimeFormatter formatter) {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("contentId", comment.getId());
        obj.put("date", formatDate(comment.getLastModificationDate(), formatter));
        obj.put("lastModifierKey", comment.getLastModifier() == null? "": comment.getLastModifier().getKey().getStringValue());
        obj.put("lastModifierUserName", comment.getLastModifier() == null? "": comment.getLastModifier().getName());
        obj.put("lastModifierFullName", comment.getLastModifier() == null? "": comment.getLastModifier().getFullName());
        obj.put("body", comment.getBodyAsStringWithoutMarkup().trim());
        return obj;
    }

    @Inject
    public CommentHistoryResource(
        final @ComponentImport LocaleManager localeManager,
        final @ComponentImport UserManager userManager,
        final @ComponentImport UserAccessor userAccessor,
        final @ComponentImport DataSourceProvider dataSourceProvider,
        final @ComponentImport CommentManager commentManager
    ) {
        this.localeManager = localeManager;
        this.userManager = userManager;
        this.userAccessor = userAccessor;
        this.dataSourceProvider = dataSourceProvider;
        this.commentManager = commentManager;
    }
}