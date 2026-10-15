// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import java.io.*;
import java.time.Clock;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 照片真实验证、重编码及授权下载，内容与元数据在同一数据库事务保存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class PhotoService {
  final Store db;
  final CleaningService core;
  final AccessService access;
  final AdminService admin;
  final Clock clock;

  /** 绑定作业、身份和事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public PhotoService(
      Store db, CleaningService core, AccessService access, AdminService admin, Clock clock) {
    this.db = db;
    this.core = core;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
  }

  /** 仅当轮已指派人员可上传前后照片，不接收任意URL或SVG。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object upload(Long rid, String phase, String caption, MultipartFile file)
      throws IOException {
    admin.lock();
    var round = db.get(WorkRound.class, rid);
    var visit = core.visibleVisit(round.visitId);
    var worker = core.workerSelf();
    Rules.check(
        core.assignedTo(round, worker.id) && core.currentRound(visit).id.equals(rid),
        "OUT_OF_SCOPE");
    Rules.check(Set.of("ASSIGNED", "IN_PROGRESS").contains(round.status), "STATE_INVALID");
    Rules.check(
        Set.of("BEFORE", "AFTER").contains(phase)
            && (!phase.equals("AFTER") || round.status.equals("IN_PROGRESS")),
        "PHOTO_PHASE_INVALID");
    Rules.check(file.getSize() > 0 && file.getSize() <= 8L * 1024 * 1024, "FILE_TOO_LARGE");
    byte[] input = file.getBytes();
    String digest;
    byte[] bytes;
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      var readers = ImageIO.getImageReaders(stream);
      Rules.check(readers.hasNext(), "IMAGE_INVALID");
      var reader = readers.next();
      try {
        reader.setInput(stream);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        Rules.check(Set.of("png", "jpeg", "jpg").contains(format), "IMAGE_INVALID");
        int width = reader.getWidth(0), height = reader.getHeight(0);
        Rules.check(
            width > 0
                && height > 0
                && width <= 4096
                && height <= 4096
                && (long) width * height <= 16000000,
            "IMAGE_DIMENSIONS");
        var image = reader.read(0);
        var out = new ByteArrayOutputStream();
        Rules.check(ImageIO.write(image, "png", out), "IMAGE_INVALID");
        bytes = out.toByteArray();
        Rules.check(bytes.length <= 8L * 1024 * 1024, "FILE_TOO_LARGE");
        digest = CleaningService.sha(bytes);
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new Problem(400, "IMAGE_INVALID");
    }
    var found =
        db.query(
            Photo.class,
            "from Photo where roundId=?1 and phase=?2 and sha256=?3",
            rid,
            phase,
            digest);
    if (!found.isEmpty()) return metadata(found.getFirst());
    Rules.check(
        db.query(Photo.class, "from Photo where roundId=?1", rid).size() < 12, "PHOTO_LIMIT");
    var order = db.get(Booking.class, visit.bookingId);
    Long used =
        db.jpql(
                Long.class,
                "select coalesce(sum(p.size),0) from Photo p, WorkRound r, Visit v, Booking b where p.roundId=r.id and r.visitId=v.id and v.bookingId=b.id and b.departmentId=:d")
            .setParameter("d", order.departmentId)
            .getSingleResult();
    Rules.check(used + bytes.length <= 256L * 1024 * 1024, "PHOTO_QUOTA");
    var p = new Photo();
    p.roundId = rid;
    p.uploaderId = access.current().id;
    p.phase = phase;
    p.fileName = UUID.randomUUID() + ".png";
    p.sha256 = digest;
    p.caption = Rules.text(caption, 300, false);
    p.size = bytes.length;
    p.createdAt = clock.instant();
    db.save(p);
    var data = new PhotoData();
    data.photoId = p.id;
    data.content = bytes;
    db.save(data);
    access.audit("PHOTO_UPLOAD", p.id, order.departmentId);
    return metadata(p);
  }

  /** 详情不带二进制、设备或服务器文件路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Object metadata(Photo p) {
    return CleaningService.row(
        "id",
        p.id,
        "phase",
        p.phase,
        "caption",
        p.caption,
        "size",
        p.size,
        "createdAt",
        p.createdAt);
  }

  /** 每次下载重新校验客户归属或当轮人员权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public byte[] read(Long id) {
    var p = db.get(Photo.class, id);
    var r = db.get(WorkRound.class, p.roundId);
    core.visibleVisit(r.visitId);
    if (!core.office() && !access.has("portal"))
      Rules.check(core.assignedTo(r, core.workerSelf().id), "OUT_OF_SCOPE");
    return db.get(PhotoData.class, id).content;
  }
}
