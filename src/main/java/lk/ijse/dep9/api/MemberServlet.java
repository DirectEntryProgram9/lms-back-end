package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;
import lk.ijse.dep9.dto.MemberDTO;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "MemberServlet", value = "/members/*", loadOnStartup = 0)
public class MemberServlet extends HttpServlet2 {
@Resource(lookup = "java:/comp/env/jdbc/lms")
    private DataSource pool;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {

            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");

            if (query != null && size != null && page != null) {
                if (!size.matches("\\d+") || !page.matches("\\d+")) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid page or size");
                }
                else {
                    searchPaginatedMembers(query, Integer.parseInt(size), Integer.parseInt(page), response);
                }
            }
            else if (query != null) {
                searchMembers(query, response);
            }
            else if (size != null && page != null) {
                loadPaginatedAllMembers(Integer.parseInt(size), Integer.parseInt(page), response);
            }
            else {
                loadAllMembers(response);
            }
        }
        else {
            Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$").matcher(request.getPathInfo());
            if (matcher.matches()) {
                getMemberDetails(matcher.group(1), response);
            }
            else {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Expect valid UUID");
            }
        }
    }

    private void loadAllMembers(HttpServletResponse response) throws IOException {
        try (Connection connection = pool.getConnection()) {
            Statement stm = connection.createStatement();
            String sql = "SELECT * FROM member";
            ResultSet rst = stm.executeQuery(sql);
            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);
                members.add(dto);
            }

            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(members, response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to load all members");
        }
    }

    private void loadPaginatedAllMembers(int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            Statement stmCount = connection.createStatement();
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member LIMIT ? OFFSET ?");
            String sql = "SELECT COUNT(id) FROM member";

            ResultSet rst = stmCount.executeQuery(sql);
            rst.next();

            int totalMembers = rst.getInt(1);
            response.addIntHeader("X-Total-Count", totalMembers);

            stm.setInt(1, size);
            stm.setInt(2, (page - 1) * size);
            ResultSet rst2 = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst2.next()) {
                String id = rst2.getString("id");
                String name = rst2.getString("name");
                String address = rst2.getString("address");
                String contact = rst2.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);
                members.add(dto);
            }

            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers", "X-Total-Count");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(members, response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to load members by page");
        }
    }

    private void searchMembers(String query, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            query = "%" + query + "%";
            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stm.setString(4, query);
            ResultSet rst = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                members.add(new MemberDTO(id, name, address, contact));
            }

            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members, response.getWriter());

        } catch (SQLException|IOException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Irrelevant search request");
        }
    }

    private void searchPaginatedMembers(String query, int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stmCount = connection.prepareStatement("SELECT COUNT(id) FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ? LIMIT ? OFFSET ?");

            query = "%" + query + "%";
            stmCount.setString(1, query);
            stmCount.setString(2, query);
            stmCount.setString(3, query);
            stmCount.setString(4, query);
            ResultSet rst = stmCount.executeQuery();
            rst.next();

            int totalMembers = rst.getInt(1);
            response.addIntHeader("X-Total-Count", totalMembers);

            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stm.setString(4, query);
            stm.setInt(5, size);
            stm.setInt(6, (page - 1) * size);

            ResultSet rst2 = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst2.next()) {
                String id = rst2.getString("id");
                String name = rst2.getString("name");
                String address = rst2.getString("address");
                String contact = rst2.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);
                members.add(dto);
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-Headers","X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers","X-Total-Count");
            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(members, response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to search members by page");
        }
    }

    private void getMemberDetails(String memberId, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id=?");
            stm.setString(1, memberId);
            ResultSet rst = stm.executeQuery();

            if (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);

                response.setContentType("application/json");
                Jsonb jsonb = JsonbBuilder.create();
                jsonb.toJson(dto, response.getWriter());
            }
            else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid member id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to get member details");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
            try {
                if (request.getContentType() == null || !request.getContentType().startsWith("application/json")) {
                    throw new JsonbException("Invalid JSON");
                }

                MemberDTO member = JsonbBuilder.create().
                        fromJson(request.getReader(), MemberDTO.class);

                if (member.getName() == null ||
                        !member.getName().matches("[A-Za-z ]+")) {
                    throw new JsonbException("Name is empty or invalid");
                } else if (member.getContact() == null ||
                        !member.getContact().matches("\\d{3}-\\d{7}")) {
                    throw new JsonbException("Contact is empty or invalid");
                } else if (member.getAddress() == null ||
                        !member.getAddress().matches("[A-Za-z0-9,.:;/\\-]+")) {
                    throw new JsonbException("Address is empty or invalid");
                }

                try (Connection connection = pool.getConnection()) {
                    member.setId(UUID.randomUUID().toString());
                    PreparedStatement stm = connection.
                            prepareStatement("INSERT INTO member (id, name, address, contact) VALUES (?, ?, ?, ?)");
                    stm.setString(1, member.getId());
                    stm.setString(2, member.getName());
                    stm.setString(3, member.getAddress());
                    stm.setString(4, member.getContact());

                    int affectedRows = stm.executeUpdate();
                    if (affectedRows == 1) {
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        response.setContentType("application/json");
                        JsonbBuilder.create().toJson(member, response.getWriter());
                    } else {
                        throw new SQLException("Something went wrong");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server cannot save the member");
                }
            } catch (JsonbException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Irrelevant request to save members");
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Server does not support the functionality required to save the member");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }

        Matcher matcher = Pattern.
                compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$")
                .matcher(request.getPathInfo());
        if (matcher.matches()) {
            deleteMember(matcher.group(1), response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "server does not support the functionality required to delete the member");
        }
    }

    private void deleteMember(String memberId, HttpServletResponse response) {
        try (Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("DELETE FROM member WHERE id=?");
            stm.setString(1, memberId);
            int affectedRows = stm.executeUpdate();
            if (affectedRows == 0) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid member id");
            } else {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT, "Request has succeeded");
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }

        Matcher matcher = Pattern.
                compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12})/?$")
                .matcher(request.getPathInfo());
        if (matcher.matches()) {
            updateMember(matcher.group(1), request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "server does not support the functionality required to update the member");
        }
    }

    private void updateMember(String memberId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (request.getContentType() == null || !request.getContentType().startsWith("application/json")) {
                throw new JsonbException("Invalid JSON");
            }
            MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);

            if (member.getId() == null || !memberId.equalsIgnoreCase(member.getId())) {
                throw new JsonbException("Id is empty or invalid");
            } else if (member.getName() == null ||
                    !member.getName().matches("[A-Za-z ]+")) {
                throw new JsonbException("Name is empty or invalid");
            } else if (member.getContact() == null ||
                    !member.getContact().matches("\\d{3}-\\d{7}")) {
                throw new JsonbException("Contact is empty or invalid");
            } else if (member.getAddress() == null ||
                    !member.getAddress().matches("[A-Za-z0-9,.:;/\\-]+")) {
                throw new JsonbException("Address is empty or invalid");
            }

            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.
                        prepareStatement("UPDATE member SET name=?, address=?, contact=? WHERE id=?");
                stm.setString(1, member.getName());
                stm.setString(2, member.getAddress());
                stm.setString(3, member.getContact());
                stm.setString(4, member.getId());

                if (stm.executeUpdate() == 1) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT, "update member request has succeeded");
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Member does not exist");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update the member");
            }
        } catch (JsonbException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "server cannot process the request due to something that is perceived to be a client error");
        }
    }
}
