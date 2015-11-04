package crawler

import microservice.crawler.{ PlayerLine, Total }
import scalaz._, Scalaz._

trait RegexSupport {

  val field = """<td(.*)>(.*)</td>""".r
  val PlayerName = """<(.*)><a href="(.*)">(.*)</a></td>""".r

  val playerKeys = List("name".some, "pos".some, "min".some, "fgmA".some, "threePmA".some, "ftmA".some,
    "minusSlashPlus".some, "offReb".some, "defReb".some, "totalReb".some, "ast".some, "pf".some,
    "steels".some, "to".some, "bs".some, "ba".some, "pts".some)

  val totalKeys = List("min".some, "fgmA".some, "threePmA".some, "ftmA".some,
    "minusSlashPlus".some, "offReb".some, "defReb".some, "totalReb".some, "ast".some, "pf".some,
    "steels".some, "to".some, "bs".some, "ba".some, "pts".some)

  def totalLens = microservice.crawler.Lens[microservice.crawler.Total]
    .+(Option("min") -> { (in: Total) =>
      (chunkWithMin: String) =>
        chunkWithMin match {
          case field(_, min0) => in.copy(min = min0.toInt)
          case other          => in
        }
    })
    .+(Option("fgmA") -> { (in: Total) =>
      (chunkWithfgmA: String) =>
        chunkWithfgmA match {
          case field(_, fgmA0) => in.copy(fgmA = fgmA0)
          case other           => in
        }
    })
    .+(Option("threePmA") -> { (in: Total) =>
      (chunkWithThreePmA: String) =>
        chunkWithThreePmA match {
          case field(_, threePmA0) => in.copy(threePmA = threePmA0)
          case other               => in
        }
    })
    .+(Option("ftmA") -> { (in: Total) =>
      (chunkWithftmA: String) =>
        chunkWithftmA match {
          case field(_, ftmA0) => in.copy(ftmA = ftmA0)
          case other           => in
        }
    })
    .+(Option("minusSlashPlus") -> { (in: Total) =>
      (chunkWithMinusSlashPlus: String) =>
        chunkWithMinusSlashPlus match {
          case field(_, minusSlashPlus0) if (minusSlashPlus0 != "&nbsp;") => in.copy(minusSlashPlus = minusSlashPlus0)
          case other => in
        }
    })
    .+(Option("offReb") -> { (in: Total) =>
      (chunkWithOffReb: String) =>
        chunkWithOffReb match {
          case field(_, offReb0) if (offReb0.trim != "") =>
            in.copy(offReb = offReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("defReb") -> { (in: Total) =>
      (chunkWithDefReb: String) =>
        chunkWithDefReb match {
          case field(_, defReb0) if (defReb0.trim != "") =>
            in.copy(defReb = defReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("totalReb") -> { (in: Total) =>
      (chunkWithTotalReb: String) =>
        chunkWithTotalReb match {
          case field(_, totalReb0) if (totalReb0.trim != "") =>
            in.copy(totalReb = totalReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("ast") -> { (in: Total) =>
      (chunckWithAst: String) =>
        chunckWithAst match {
          case field(_, ast0) if (ast0.trim != "") =>
            in.copy(ast = ast0.trim.toInt)
          case other => in
        }
    })
    .+(Option("pf") -> { (in: Total) =>
      (chunkWithpf: String) =>
        chunkWithpf match {
          case field(_, pf0) if (pf0.trim != "") =>
            in.copy(pf = pf0.trim.toInt)
          case other => in
        }
    })
    .+(Option("steels") -> { (in: Total) =>
      (chunkWithSteels: String) =>
        chunkWithSteels match {
          case field(_, steels0) if (steels0.trim != "") =>
            in.copy(steels = steels0.trim.toInt)
          case other => in
        }
    })
    .+(Option("to") -> { (in: Total) =>
      (chunkWithto: String) =>
        chunkWithto match {
          case field(_, to0) if (to0.trim != "") =>
            in.copy(to = to0.trim.toInt)
          case other => in
        }
    })
    .+(Option("bs") -> { (in: Total) =>
      (chunkWithbs: String) =>
        chunkWithbs match {
          case field(_, bs0) if (bs0.trim != "") =>
            in.copy(bs = bs0.trim.toInt)
          case other => in
        }
    })
    .+(Option("ba") -> { (in: Total) =>
      (chunkWithba: String) =>
        chunkWithba match {
          case field(_, ba0) if (ba0.trim != "") =>
            in.copy(ba = ba0.trim.toInt)
          case other => in
        }
    })
    .+(Option("pts") -> { (in: Total) =>
      (chunkWithpts: String) =>
        chunkWithpts match {
          case field(_, pts0) if (pts0.trim != "") =>
            in.copy(pts = pts0.trim.toInt)
          case other => in
        }
    })

  def playerLens = microservice.crawler.Lens[microservice.crawler.PlayerLine]
    .+(Option("name") -> { (in: PlayerLine) =>
      (chunkWithName: String) =>
        chunkWithName match {
          case PlayerName(_, _, name0) => in.copy(name = name0)
          case other                   => in
        }
    })
    .+(Option("pos") -> { (in: PlayerLine) =>
      (chunkWithPos: String) =>
        chunkWithPos match {
          case field(_, v) if (v != "&nbsp;") => in.copy(pos = v)
          case other                          => in
        }
    })
    .+(Option("min") -> { (in: PlayerLine) =>
      (chunkWithMin: String) =>
        chunkWithMin match {
          case field(_, min0) => in.copy(min = min0)
          case other          => in
        }
    })
    .+(Option("fgmA") -> { (in: PlayerLine) =>
      (chunkWithfgmA: String) =>
        chunkWithfgmA match {
          case field(_, fgmA0) => in.copy(fgmA = fgmA0)
          case other           => in
        }
    })
    .+(Option("threePmA") -> { (in: PlayerLine) =>
      (chunkWithThreePma: String) =>
        chunkWithThreePma match {
          case field(_, threePma0) => in.copy(threePmA = threePma0)
          case other               => in
        }
    })
    .+(Option("ftmA") -> { (in: PlayerLine) =>
      (chunkWithFtmA: String) =>
        chunkWithFtmA match {
          case field(_, ftmA0) => in.copy(ftmA = ftmA0)
          case other           => in
        }
    })
    .+(Option("minusSlashPlus") -> { (in: PlayerLine) =>
      (chunkWithMinusSlashPlus: String) =>
        chunkWithMinusSlashPlus match {
          case field(_, minusSlashPlus0) => in.copy(minusSlashPlus = minusSlashPlus0)
          case other                     => in
        }
    })
    .+(Option("offReb") -> { (in: PlayerLine) =>
      (chunkWithOffReb: String) =>
        chunkWithOffReb match {
          case field(_, offReb0) if (offReb0.trim != "") =>
            in.copy(offReb = offReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("defReb") -> { (in: PlayerLine) =>
      (chunkWithDefReb: String) =>
        chunkWithDefReb match {
          case field(_, defReb0) if (defReb0.trim != "") =>
            in.copy(defReb = defReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("totalReb") -> { (in: PlayerLine) =>
      (chunkWithTotalReb: String) =>
        chunkWithTotalReb match {
          case field(_, totalReb0) if (totalReb0.trim != "") =>
            in.copy(totalReb = totalReb0.trim.toInt)
          case other => in
        }
    })
    .+(Option("ast") -> { (in: PlayerLine) =>
      (chunckWithAst: String) =>
        chunckWithAst match {
          case field(_, ast0) if (ast0.trim != "") =>
            in.copy(ast = ast0.trim.toInt)
          case other => in
        }
    })
    .+(Option("pf") -> { (in: PlayerLine) =>
      (chunkWithpf: String) =>
        chunkWithpf match {
          case field(_, pf0) if (pf0.trim != "") =>
            in.copy(pf = pf0.trim.toInt)
          case other => in
        }
    })
    .+(Option("steels") -> { (in: PlayerLine) =>
      (chunkWithSteels: String) =>
        chunkWithSteels match {
          case field(_, steels0) if (steels0.trim != "") =>
            in.copy(steels = steels0.trim.toInt)
          case other => in
        }
    })
    .+(Option("to") -> { (in: PlayerLine) =>
      (chunkWithto: String) =>
        chunkWithto match {
          case field(_, to0) if (to0.trim != "") =>
            in.copy(to = to0.trim.toInt)
          case other => in
        }
    })
    .+(Option("bs") -> { (in: PlayerLine) =>
      (chunkWithbs: String) =>
        chunkWithbs match {
          case field(_, bs0) if (bs0.trim != "") =>
            in.copy(bs = bs0.trim.toInt)
          case other => in
        }
    })
    .+(Option("ba") -> { (in: PlayerLine) =>
      (chunkWithba: String) =>
        chunkWithba match {
          case field(_, ba0) if (ba0.trim != "") =>
            in.copy(ba = ba0.trim.toInt)
          case other => in
        }
    })
    .+(Option("pts") -> { (in: PlayerLine) =>
      (chunkWithpts: String) =>
        chunkWithpts match {
          case field(_, pts0) if (pts0.trim != "") =>
            in.copy(pts = pts0.trim.toInt)
          case other => in
        }
    })
}