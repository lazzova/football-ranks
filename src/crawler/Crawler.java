package crawler;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler {
	public static Database db = new Database();
	
	
	public static void main(String[] args) {
		// fetch country data
		Crawler cw = new Crawler();
		Elements countryData = null;
		try {
			countryData = cw.fetchCountries();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// add countries to database
		for (Element country : countryData) {			
			try {
				cw.addCountryToDatabase(country.text());
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		
		// fetch matches statistics and store them into database
		String base = "http://www.11v11.com";
		Elements matches = null;
		String c = null;
		for (Element country : countryData) {
			c = country.text();
			try {
				matches = cw.fetchMatches(base + country.attr("href") + "tab/stats");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			for (Element match : matches) {
				try {
					cw.addMatchToDatabase(match, c);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}	
			}
		}	
	}
	
		
	/**
	 * Fetches all the countries
	 * 
	 * @return Elements
	 * @throws IOException
	 */
	public Elements  fetchCountries() throws IOException {
		final List<String> confederations = new ArrayList<String>(
				Arrays.asList(new String[]{"caf","afc","concacaf","conmebol","ofc","uefa"})); 
		Elements countries = new Elements ();
				
		
		for (String c : confederations) {
			countries.addAll(fetchCountries(c));
		}
		
		return countries;		
	}

	
	/**
	 * Returns links to all countries statistics in a given confederation
	 * 
	 * @param confederation
	 * @return Elements
	 * @throws IOException
	 */
	public Elements fetchCountries (String confederation) throws IOException {
		String base = "http://www.11v11.com";
		Document doc = Jsoup.connect(base + "/" + confederation).get();
		Elements data = doc.select("a[href^=/teams/]");
		
		return data;
	}	
	
	
	/**
	 * Inserts a country into database
	 * 
	 * @param country
	 * @throws SQLException
	 */
	public void addCountryToDatabase (String country) throws SQLException {
		String query = "INSERT INTO `football`.`countries` " + "(`country`) VALUES " + 
						"(?) ON DUPLICATE KEY UPDATE country=country;";
		PreparedStatement stmt = db.connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
		stmt.setString(1, country);
		stmt.execute();		
	}
	
	
	/**
	 * Fetches all the matches for the given national team's url
	 * 
	 * @param url
	 * @return Elements
	 * @throws IOException
	 */
	public Elements fetchMatches (String url) throws IOException {
		System.out.println(url);
		Document doc = Jsoup.connect(url).get();
		Elements data = doc.select("tr");
		data.remove(0);     // titles of the table columns
				
		return data;
	}
	
	
	/**
	 * Writes all matches for a given national team to a database
	 * 
	 * @param match
	 * @param team1
	 * @throws SQLException
	 */
	public void addMatchToDatabase (Element match, String team1) throws SQLException {
		String temp;
		int tmp;
		
		String team2 = match.child(0).text();
		int games = Integer.parseInt(match.child(1).text());
		int t1_wins = Integer.parseInt(match.child(2).text());
		int draws = Integer.parseInt(match.child(3).text());
		int t2_wins = Integer.parseInt(match.child(4).text());
				
		temp = match.child(5).text();
		int t1_goals = temp.isEmpty() ? 0 : Integer.parseInt(temp);
		
		temp = match.child(6).text();
		int t2_goals = temp.isEmpty() ? 0 : Integer.parseInt(temp);  // if no data assume 0 goals
		
		// should be in alphabetical order in the data base
		if (team1.compareToIgnoreCase(team2) > 0) {
			temp = team1; team1 = team2; team2 = temp;
			tmp = t1_wins; t1_wins = t2_wins; t2_wins = tmp;
			tmp = t1_goals; t1_goals = t2_goals; t2_goals = tmp;
		}
		
		ResultSet r1 = db.fetchExecute(String.format("SELECT id FROM football.countries WHERE (country = '%s');", team1));
		ResultSet r2 = db.fetchExecute(String.format("SELECT id FROM football.countries WHERE (country = '%s');", team2));
		
		if (!r1.first() || !r2.first())
			return;   // no such a country
		
		int t1 = r1.getInt("id");
		int t2 = r2.getInt("id");
		
		
		String queryFormat = "INSERT INTO football.matches " + 
				"(team1, team2, games, t1_wins, draws, t2_wins, t1_goals, t2_goals) " +
				"VALUES (%d, %d, %d, %d, %d, %d, %d, %d) ON DUPLICATE KEY UPDATE " +
				"team1=team1, team2=team2, games=games, t1_wins=t1_wins, " + 
				"draws=draws, t2_wins=t2_wins, t1_goals=t1_goals, t2_goals=t2_goals;";
		
		db.execute(String.format(queryFormat, t1, t2, games, t1_wins, draws, 
				t2_wins, t1_goals, t2_goals));
				
		System.out.println("Database updated");
	}
	
}


