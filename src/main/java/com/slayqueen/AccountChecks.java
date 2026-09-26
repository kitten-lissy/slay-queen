package com.slayqueen;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;

/**
 * Checks quests, skill levels and achievement diaries for task requirements.
 * Must be used on the client thread. Unknown names count as met, so bad data never
 * shows a false "missing requirement".
 */
@Singleton
public class AccountChecks implements TaskResolver.Account
{
	private final Client client;

	@Inject
	AccountChecks(Client client)
	{
		this.client = client;
	}

	@Override
	public int slayerLevel()
	{
		return client.getRealSkillLevel(Skill.SLAYER);
	}

	@Override
	public boolean met(TaskInfo.Requirement r)
	{
		if (r == null || r.getType() == null)
		{
			return true;
		}
		switch (r.getType())
		{
			case "QUEST":
				return questDone(r.getQuest(), r.isStarted());
			case "SKILL":
				return skillAtLeast(r.getSkill(), r.getLevel());
			case "DIARY":
				int varbit = diaryVarbit(r.getDiary(), r.getTier());
				return varbit < 0 || client.getVarbitValue(varbit) == 1;
			default:
				return true;
		}
	}

	private boolean questDone(String name, boolean startedIsEnough)
	{
		for (Quest q : Quest.values())
		{
			if (q.name().equals(name))
			{
				QuestState state = q.getState(client);
				return startedIsEnough ? state != QuestState.NOT_STARTED : state == QuestState.FINISHED;
			}
		}
		return true;
	}

	private boolean skillAtLeast(String name, int level)
	{
		for (Skill s : Skill.values())
		{
			if (s.name().equals(name))
			{
				return client.getRealSkillLevel(s) >= level;
			}
		}
		return true;
	}

	/**
	 * Varbit that is 1 when the diary tier is complete, or -1 if unknown.
	 */
	static int diaryVarbit(String diary, String tier)
	{
		String key = (diary == null ? "" : diary) + "_" + (tier == null ? "" : tier);
		switch (key)
		{
			case "ARDOUGNE_EASY": return VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE;
			case "ARDOUGNE_MEDIUM": return VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE;
			case "ARDOUGNE_HARD": return VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE;
			case "ARDOUGNE_ELITE": return VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE;
			case "DESERT_EASY": return VarbitID.DESERT_DIARY_EASY_COMPLETE;
			case "DESERT_MEDIUM": return VarbitID.DESERT_DIARY_MEDIUM_COMPLETE;
			case "DESERT_HARD": return VarbitID.DESERT_DIARY_HARD_COMPLETE;
			case "DESERT_ELITE": return VarbitID.DESERT_DIARY_ELITE_COMPLETE;
			case "FALADOR_EASY": return VarbitID.FALADOR_DIARY_EASY_COMPLETE;
			case "FALADOR_MEDIUM": return VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE;
			case "FALADOR_HARD": return VarbitID.FALADOR_DIARY_HARD_COMPLETE;
			case "FALADOR_ELITE": return VarbitID.FALADOR_DIARY_ELITE_COMPLETE;
			case "FREMENNIK_EASY": return VarbitID.FREMENNIK_DIARY_EASY_COMPLETE;
			case "FREMENNIK_MEDIUM": return VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE;
			case "FREMENNIK_HARD": return VarbitID.FREMENNIK_DIARY_HARD_COMPLETE;
			case "FREMENNIK_ELITE": return VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE;
			case "KANDARIN_EASY": return VarbitID.KANDARIN_DIARY_EASY_COMPLETE;
			case "KANDARIN_MEDIUM": return VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE;
			case "KANDARIN_HARD": return VarbitID.KANDARIN_DIARY_HARD_COMPLETE;
			case "KANDARIN_ELITE": return VarbitID.KANDARIN_DIARY_ELITE_COMPLETE;
			case "KARAMJA_ELITE": return VarbitID.KARAMJA_DIARY_ELITE_COMPLETE;
			case "KOUREND_EASY": return VarbitID.KOUREND_DIARY_EASY_COMPLETE;
			case "KOUREND_MEDIUM": return VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE;
			case "KOUREND_HARD": return VarbitID.KOUREND_DIARY_HARD_COMPLETE;
			case "KOUREND_ELITE": return VarbitID.KOUREND_DIARY_ELITE_COMPLETE;
			case "LUMBRIDGE_EASY": return VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE;
			case "LUMBRIDGE_MEDIUM": return VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE;
			case "LUMBRIDGE_HARD": return VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE;
			case "LUMBRIDGE_ELITE": return VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE;
			case "MORYTANIA_EASY": return VarbitID.MORYTANIA_DIARY_EASY_COMPLETE;
			case "MORYTANIA_MEDIUM": return VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE;
			case "MORYTANIA_HARD": return VarbitID.MORYTANIA_DIARY_HARD_COMPLETE;
			case "MORYTANIA_ELITE": return VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE;
			case "VARROCK_EASY": return VarbitID.VARROCK_DIARY_EASY_COMPLETE;
			case "VARROCK_MEDIUM": return VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE;
			case "VARROCK_HARD": return VarbitID.VARROCK_DIARY_HARD_COMPLETE;
			case "VARROCK_ELITE": return VarbitID.VARROCK_DIARY_ELITE_COMPLETE;
			case "WESTERN_EASY": return VarbitID.WESTERN_DIARY_EASY_COMPLETE;
			case "WESTERN_MEDIUM": return VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE;
			case "WESTERN_HARD": return VarbitID.WESTERN_DIARY_HARD_COMPLETE;
			case "WESTERN_ELITE": return VarbitID.WESTERN_DIARY_ELITE_COMPLETE;
			case "WILDERNESS_EASY": return VarbitID.WILDERNESS_DIARY_EASY_COMPLETE;
			case "WILDERNESS_MEDIUM": return VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE;
			case "WILDERNESS_HARD": return VarbitID.WILDERNESS_DIARY_HARD_COMPLETE;
			case "WILDERNESS_ELITE": return VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE;
			default: return -1;
		}
	}
}
