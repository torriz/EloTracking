package com.elorankingbot.backend.service;

import com.elorankingbot.backend.command.CommandClassScanner;
import com.elorankingbot.backend.dao.*;
import com.elorankingbot.backend.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DBService {

	private static float initialRating = 1200;
	private static float k = 16;
	private final DiscordBotService bot;
	private final ServerDao serverDao;
	private final ChallengeDao challengeDao;
	private final MatchResultDao matchResultDao;
	private final MatchResultReferenceDao matchResultReferenceDao;
	private final PlayerDao playerDao;
	private final TimeSlotDao timeSlotDao;
	private final MatchDao matchDao;
	private final RankingsEntryDao rankingsEntryDao;

	@Autowired
	public DBService(Services services, CommandClassScanner scanner,
					 ServerDao serverDao, ChallengeDao challengeDao, MatchResultDao matchResultDao,
					 MatchResultReferenceDao matchResultReferenceDao, PlayerDao playerDao,
					 TimeSlotDao timeSlotDao, MatchDao matchDao, RankingsEntryDao rankingsEntryDao) {
		this.bot = services.bot;
		this.serverDao = serverDao;
		this.challengeDao = challengeDao;
		this.matchResultDao = matchResultDao;
		this.matchResultReferenceDao = matchResultReferenceDao;
		this.playerDao = playerDao;
		this.timeSlotDao = timeSlotDao;
		this.matchDao = matchDao;
		this.rankingsEntryDao = rankingsEntryDao;
	}

	public void resetAllPlayerRatings(Game game) {
		log.debug(String.format("Resetting all player ratings for %s on %s", game.getName(), bot.getServerName(game.getServer())));
		List<Player> players = playerDao.findAllByGuildId(game.getGuildId());
		players.forEach(player -> {
			PlayerGameStats gameStats = player.getOrCreateGameStats(game);
			gameStats.setRating(initialRating);
			gameStats.setWins(0);
			gameStats.setLosses(0);
			gameStats.setDraws(0);
		});
		playerDao.saveAll(players);
		rankingsEntryDao.deleteAllByGuildIdAndAndGameName(game.getGuildId(), game.getName());
	}

	// Server
	public Optional<Server> findServerByGuildId(long guildId) {
		return serverDao.findById(guildId);
	}

	public Server getServerByGuildId(long guildId) {
		return findServerByGuildId(guildId).get();
	}

	public void saveServer(Server server) {
		log.debug(String.format("Saving server %s", bot.getServerName(server)));
		serverDao.save(server);
	}

	public List<Server> findAllServers() {
		return serverDao.findAll();
	}

	// Match
	public Match getMatch(UUID matchId) {
		return matchDao.findById(matchId).get();
	}

	public Optional<Match> findMatch(UUID matchId) {
		return matchDao.findById(matchId);
	}

	public void saveMatch(Match match) {
		log.debug(String.format("Saving match %s on %s: %s",
				match.getId(),
				bot.getServerName(match.getServer()),
				match.getPlayers().stream().map(Player::getTag).collect(Collectors.joining(","))));
		matchDao.save(match);
	}

	public void deleteMatch(Match match) {
		log.debug(String.format("Deleting match %s on %s: %s",
				match.getId(),
				bot.getServerName(match.getServer()),
				match.getPlayers().stream().map(Player::getTag).collect(Collectors.joining(","))));
		matchDao.delete(match);
	}

	public void deleteAllMatches(Game game) {
		log.debug(String.format("Deleting all match for %s on %s", game.getName(), bot.getServerName(game.getServer())));
		matchDao.deleteAllByServerAndGameId(game.getServer(), game.getName());
	}

	public List<Match> findAllMatchesByServer(Server server) {
		return matchDao.findAllByServer(server);
	}

	public List<Match> findAllMatchesByPlayer(Player player) {
		return findAllMatchesByServer(getServerByGuildId(player.getGuildId()))
				.stream().filter(match -> match.containsPlayer(player.getId()))
				.toList();
	}

	// MatchResult
	public void saveMatchResult(MatchResult matchResult) {
		log.debug(String.format("saving match result %s for %s on %s",
				matchResult.getId(),
				matchResult.getGame().getName(),
				bot.getServerName(matchResult.getServer())));
		matchResultDao.save(matchResult);
	}

	public Optional<MatchResult> findMatchResult(UUID id) {
		return matchResultDao.findById(id);
	}

	public void deleteAllMatchResults(Game game) {
		log.debug(String.format("Deleting all match results for %s on %s",
				game.getName(),
				bot.getServerName(game.getServer())));
		matchResultDao.deleteAllByServerAndGameName(game.getServer(), game.getName());
	}

	// MatchResultReference
	public void saveMatchResultReference(MatchResultReference matchResultReference) {
		matchResultReferenceDao.save(matchResultReference);
	}

	public Optional<MatchResultReference> findMatchResultReference(long messageId) {
		Optional<MatchResultReference> maybeMatchResultReference = matchResultReferenceDao.findByResultMessageId(messageId);
		if (maybeMatchResultReference.isEmpty()) {
			maybeMatchResultReference = matchResultReferenceDao.findByMatchMessageId(messageId);
			if (maybeMatchResultReference.isEmpty()) {
				return Optional.empty();
			}
		}
		return maybeMatchResultReference;
	}

	// Challenge
	public Optional<ChallengeModel> findChallengeByParticipants(long guildId, long challengerId, long acceptorId) {
		//return challengeDao.findAllByGuildIdAndChallengerId(guildId, challengerId).stream()
		//		.filter(challenge -> challenge.getAcceptorUserId() == acceptorId)
		//		.findAny();
		return null;
	}

	public Optional<ChallengeModel> findChallengeById(UUID id) {
		return challengeDao.findById(id);
	}

	public void saveChallenge(ChallengeModel challenge) {
		log.debug("saving challenge " + challenge.getId());
		challengeDao.save(challenge);
	}

	public void deleteChallenge(ChallengeModel challenge) {
		//deleteChallengeById(challenge.getId());
	}

	public void deleteChallengeById(UUID id) {
		challengeDao.deleteById(id);
	}

	public List<ChallengeModel> findAllChallengesByGuildIdAndPlayerId(long guildId, long playerId) {
		List<ChallengeModel> allChallengesForPlayer = new ArrayList<>();
		//allChallengesForPlayer.addAll(challengeDao.findAllByGuildIdAndChallengerId(guildId, playerId));
		//allChallengesForPlayer.addAll(challengeDao.findAllByGuildIdAndAcceptorId(guildId, playerId));
		return allChallengesForPlayer;
	}

	// Match legacy

	public Optional<MatchResult> findMostRecentMatchResult(long guildId, long player1Id, long player2Id) {
		return null;
		/*
		Optional<Match> search = matchDao.findFirstByGuildIdAndWinnerIdAndLoserIdOrderByDate(guildId, player1Id, player2Id);
		Optional<Match> searchReverseParams = matchDao.findFirstByGuildIdAndWinnerIdAndLoserIdOrderByDate(guildId, player2Id, player1Id);

		if (search.isEmpty()) {
			return searchReverseParams;
		} else if (searchReverseParams.isEmpty()) {
			return search;
		} else {
			if (search.get().getDate().after(searchReverseParams.get().getDate())) {
				return search;
			} else {
				return searchReverseParams;
			}
		}

		 */
	}

	// Player
	public void savePlayer(Player player) {
		log.debug(String.format("saving player %s on %s", player.getTag(), bot.getServerName(player)));
		playerDao.save(player);
	}

	public void saveAllPlayers(List<Player> players) {
		log.debug(String.format("Saving players %s on %s",
				String.join(",", players.stream().map(Player::getTag).toList()),
				bot.getServerName(players.get(0))));
		playerDao.saveAll(players);
	}

	public Optional<Player> findPlayerByGuildIdAndUserId(long guildId, long userId) {
		return playerDao.findById(Player.generateId(guildId, userId));
	}

	public Player getPlayerOrGenerateIfNotPresent(long guildId, long userId, String tag) {
		Optional<Player> maybePlayer = playerDao.findById(Player.generateId(guildId, userId));
		if (maybePlayer.isPresent()) return maybePlayer.get();

		Player player = new Player(guildId, userId, tag);
		playerDao.save(player);
		return player;
	}

	public List<Player> findAllPlayersForServer(Server server) {
		return playerDao.findAllByGuildId(server.getGuildId());
	}

	// Rankings
	public RankingsExcerpt getLeaderboard(Game game) {
		// TODO nur ausschnitte abrufen, wahrscheinlich mit PagingAndSortingRepository?
		List<RankingsEntry> allEntries = rankingsEntryDao.getAllByGuildIdAndAndGameName(game.getGuildId(), game.getName());
		int numTotalPlayers = allEntries.size();
		Collections.sort(allEntries);
		return new RankingsExcerpt(game, allEntries.subList(0, Math.min(game.getLeaderboardLength(), allEntries.size())),
				1, Optional.empty(), numTotalPlayers);
	}

	public RankingsExcerpt getRankingsExcerptForPlayer(Game game, Player player) {
		// TODO wie oben
		List<RankingsEntry> allEntries = rankingsEntryDao.getAllByGuildIdAndAndGameName(game.getGuildId(), game.getName());
		int numTotalPlayers = allEntries.size();
		Collections.sort(allEntries);
		Optional<Integer> maybePlayerRankingsEntryIndex = allEntries.stream()
				.filter(rankingsEntry -> rankingsEntry.getPlayerTag().equals(player.getTag()))
				.map(allEntries::indexOf).findAny();

		List<RankingsEntry> entries;
		int lowestIndex;
		if (maybePlayerRankingsEntryIndex.isPresent()) {
			int centerIndex = maybePlayerRankingsEntryIndex.get();
			int excerptLength = 20;// TODO
			lowestIndex = Math.max(0, centerIndex - excerptLength / 2);// TODO immer 20 zeigen, auch am rand
			int highestIndex = Math.min(allEntries.size(), centerIndex + excerptLength / 2);
			entries = allEntries.subList(lowestIndex, highestIndex);
		} else {
			entries = new ArrayList<>();
			lowestIndex = -2;
		}

		return new RankingsExcerpt(game, entries, lowestIndex + 1, Optional.of(player.getTag()), numTotalPlayers);
	}

	public boolean updateRankingsEntries(MatchResult matchResult) {
		for (PlayerMatchResult playerMatchResult : matchResult.getAllPlayerMatchResults()) {
			Optional<RankingsEntry> maybeRankingsEntry = rankingsEntryDao
					.findByGuildIdAndGameNameAndPlayerTag(matchResult.getGame().getServer().getGuildId(),
							matchResult.getGame().getName(), playerMatchResult.getPlayerTag());
			maybeRankingsEntry.ifPresent(rankingsEntryDao::delete);
			rankingsEntryDao.save(new RankingsEntry(matchResult.getGame(), playerMatchResult.getPlayer()));
		}
		return true;// TODO! pagination etc - aendert sich ueberhaupt was?
	}

	public void deleteAllRankingsEntries(Game game) {
		rankingsEntryDao.deleteAllByGuildIdAndAndGameName(game.getGuildId(), game.getName());
	}
}
