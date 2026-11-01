import { BadRequestException, ConflictException, ForbiddenException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import { AuditType, DocumentStatus, Prisma, Role, SignerStatus } from '@prisma/client';
import { PrismaService } from '../prisma.service';
import { AuthUser } from '../common/auth';
import { FieldDto, ParticipantDto, SendDto, SignDto } from './dto';
import { cleanBase64, sha256, stampPdf, validatePdf } from './pdf.util';
import { NotificationsService } from '../notifications/notifications.service';

const META = {
  id: true, ownerId: true, titulo: true, status: true, versao: true, hashSha256: true, tamanhoBytes: true, nomeArquivo: true,
  totalPaginas: true, hashFinal: true, mensagem: true, criadoEm: true, enviadoEm: true, concluidoEm: true, atualizadoEm: true,
} satisfies Prisma.documentSelect;

const USER_PUB = { select: { id: true, nome: true, email: true, telefone: true } };

type Ctx = { ip: string | null; userAgent: string | null };

@Injectable()
export class DocumentsService {
  private readonly logger = new Logger('Documents');
  constructor(
    private prisma: PrismaService,
    private notifications: NotificationsService,
  ) {}

  private async event(
    tx: Prisma.TransactionClient, doc: { id: string; versao: number; hashSha256: string }, tipo: AuditType, user: AuthUser | null,
    descricao: string, ctx?: Ctx, metadados?: Record<string, unknown>, hash?: string,
  ) {
    await tx.auditevent.create({
      data: {
        documentId: doc.id, tipo, actorId: user?.id, actorRole: user?.role, descricao, versao: doc.versao, ip: ctx?.ip ?? null,
        hashDocumentoNoMomento: hash ?? doc.hashSha256,
        metadados: metadados ? (metadados as Prisma.InputJsonValue) : Prisma.JsonNull,
      },
    });
  }

  /** Garante acesso: dono (admin) ou participante convidado. */
  async access(user: AuthUser, id: string) {
    const doc = await this.prisma.document.findUnique({ where: { id }, select: META });
    if (!doc) throw new NotFoundException('Documento não encontrado.');
    if (doc.ownerId === user.id) return { doc, isOwner: true, participant: null };
    const participant = await this.prisma.documentsigner.findUnique({ where: { documentId_signerId: { documentId: id, signerId: user.id } } });
    if (!participant || doc.status === DocumentStatus.DRAFT) {
      throw new ForbiddenException('Você não tem permissão para acessar este documento.');
    }
    return { doc, isOwner: false, participant };
  }

  async owned(user: AuthUser, id: string) {
    const a = await this.access(user, id);
    if (!a.isOwner) throw new ForbiddenException('Apenas o criador do documento pode realizar esta ação.');
    return a.doc;
  }

  private assertDraft(doc: { status: DocumentStatus }) {
    if (doc.status !== DocumentStatus.DRAFT) {
      throw new ConflictException('Documento já enviado: alterações exigem uma nova versão (POST /documents/:id/version).');
    }
  }

  async create(user: AuthUser, titulo: string | undefined, file: Express.Multer.File | undefined, ctx: Ctx) {
    if (!titulo || !titulo.trim()) throw new BadRequestException('O título do documento é obrigatório.');
    if (titulo.length > 200) throw new BadRequestException('O título deve ter no máximo 200 caracteres.');
    const info = await validatePdf(file);
    const buf = file!.buffer;
    const hash = sha256(buf);
    const doc = await this.prisma.$transaction(async (tx) => {
      const d = await tx.document.create({
        data: {
          ownerId: user.id, titulo: titulo.trim(), hashSha256: hash, tamanhoBytes: buf.length, nomeArquivo: file!.originalname || 'documento.pdf',
          totalPaginas: info.pages, pdf: buf,
        },
        select: META,
      });
      await tx.documentversion.create({ data: { documentId: d.id, versao: 1, hashSha256: hash, nomeArquivo: d.nomeArquivo, tamanhoBytes: buf.length, pdf: buf } });
      await this.event(tx, d, AuditType.CREATED, user, `Documento "${d.titulo}" criado por ${user.nome}.`, ctx, { paginas: info.pages, tamanhosPaginas: info.sizes });
      return d;
    });
    this.logger.log(`Documento ${doc.id} criado por ${user.email}`);
    return { ...doc, paginas: info.sizes };
  }

  async list(user: AuthUser, status?: DocumentStatus, situacao?: string) {
    if (user.role === Role.ADMIN) {
      const docs = await this.prisma.document.findMany({
        where: { ownerId: user.id, ...(status ? { status } : {}) },
        select: { ...META, signers: { select: { status: true, required: true } } },
        orderBy: { atualizadoEm: 'desc' },
      });
      return docs.map(({ signers, ...d }) => ({
        ...d, papel: 'OWNER',
        totalSignatarios: signers.length, assinados: signers.filter((s) => s.status === SignerStatus.SIGNED).length,
      }));
    }
    const sStatus: Prisma.documentsignerWhereInput =
      situacao === 'pendentes' ? { status: { in: [SignerStatus.PENDING, SignerStatus.VIEWED] } }
      : situacao === 'assinados' ? { status: SignerStatus.SIGNED }
      : situacao === 'recusados' ? { status: SignerStatus.REJECTED } : {};
    const parts = await this.prisma.documentsigner.findMany({
      where: { signerId: user.id, ...sStatus, document: { status: status ? status : { not: DocumentStatus.DRAFT } } },
      include: { document: { select: { ...META, owner: USER_PUB } } },
      orderBy: { invitedAt: 'desc' },
    });
    return parts.map((p) => ({ ...p.document, papel: 'SIGNER', minhaSituacao: p.status, meuPapel: p.roleLabel, assinadoEm: p.signedAt }));
  }

  async detail(user: AuthUser, id: string, ctx: Ctx) {
    const { doc, isOwner, participant } = await this.access(user, id);
    if (participant && participant.status === SignerStatus.PENDING && doc.status !== DocumentStatus.CANCELLED) {
      await this.prisma.$transaction(async (tx) => {
        await tx.documentsigner.update({ where: { id: participant.id }, data: { status: SignerStatus.VIEWED, viewedAt: new Date() } });
        await this.event(tx, doc, AuditType.VIEWED, user, `${user.nome} visualizou o documento.`, ctx);
      });
    }
    const full = await this.prisma.document.findUniqueOrThrow({
      where: { id },
      select: {
        ...META,
        owner: USER_PUB,
        signers: {
          orderBy: { ordem: 'asc' },
          select: {
            id: true, signerId: true, status: true, roleLabel: true, required: true, invitedAt: true, viewedAt: true, signedAt: true,
            rejectedAt: true, motivoRecusa: true, signer: USER_PUB, gestureMetadata: true,
          },
        },
        fields: { orderBy: [{ page: 'asc' }, { y: 'asc' }] },
        events: { orderBy: { timestamp: 'asc' } },
        versions: { select: { versao: true, hashSha256: true, nomeArquivo: true, tamanhoBytes: true, criadoEm: true }, orderBy: { versao: 'asc' } },
      },
    });
    const fields = isOwner ? full.fields : full.fields.filter((f) => f.signerId === user.id);
    return { ...full, fields, todosCampos: isOwner ? undefined : full.fields.length, papel: isOwner ? 'OWNER' : 'SIGNER', possuiDocumentoFinal: !!full.hashFinal };
  }

  async file(user: AuthUser, id: string, tipo: string) {
    await this.access(user, id);
    const d = await this.prisma.document.findUniqueOrThrow({ where: { id }, select: { pdf: true, pdfFinal: true, nomeArquivo: true, status: true } });
    if (tipo === 'final') {
      if (!d.pdfFinal) throw new NotFoundException('O documento final só fica disponível após a conclusão de todas as assinaturas.');
      return { buf: Buffer.from(d.pdfFinal), nome: d.nomeArquivo.replace(/\.pdf$/i, '') + '-assinado.pdf' };
    }
    return { buf: Buffer.from(d.pdf), nome: d.nomeArquivo };
  }

  private async validateSigners(user: AuthUser, ids: string[]) {
    const uniq = [...new Set(ids)];
    if (!uniq.length) return;
    const found = await this.prisma.user.findMany({ where: { id: { in: uniq }, adminId: user.id, role: Role.SIGNER }, select: { id: true, nome: true, email: true } });
    if (found.length !== uniq.length) throw new BadRequestException('Um ou mais signatários informados não existem ou não pertencem a você.');
    for (const s of found) if (!s.nome?.trim() || !s.email?.trim()) throw new BadRequestException(`O signatário ${s.id} está sem nome ou e-mail.`);
  }

  private validateFields(fields: FieldDto[], totalPaginas: number) {
    fields.forEach((f, i) => {
      const n = i + 1;
      if (f.page > totalPaginas) throw new BadRequestException(`Campo ${n}: a página ${f.page} não existe (o PDF tem ${totalPaginas} página(s)).`);
      const eps = 1e-6;
      if (f.x < 0 || f.y < 0 || f.x + f.width > 1 + eps || f.y + f.height > 1 + eps) {
        throw new BadRequestException(`Campo ${n}: o campo de assinatura deve permanecer dentro dos limites da página.`);
      }
    });
  }

  private async writeParticipants(tx: Prisma.TransactionClient, docId: string, parts: ParticipantDto[]) {
    const ids = parts.map((p) => p.signerId);
    if (new Set(ids).size !== ids.length) throw new BadRequestException('O mesmo signatário foi informado mais de uma vez.');
    await tx.documentsigner.deleteMany({ where: { documentId: docId } });
    if (parts.length) {
      await tx.documentsigner.createMany({
        data: parts.map((p, i) => ({ documentId: docId, signerId: p.signerId, roleLabel: p.roleLabel, required: p.required ?? true, ordem: i })),
      });
    }
  }

  private async writeFields(tx: Prisma.TransactionClient, docId: string, fields: FieldDto[]) {
    const parts = await tx.documentsigner.findMany({ where: { documentId: docId } });
    const roleOf = new Map(parts.map((p) => [p.signerId, p.roleLabel]));
    await tx.signaturefield.deleteMany({ where: { documentId: docId } });
    if (fields.length) {
      await tx.signaturefield.createMany({
        data: fields.map((f) => ({
          documentId: docId, signerId: f.signerId, page: f.page, x: f.x, y: f.y, width: f.width, height: f.height,
          required: f.required ?? true, roleLabel: f.roleLabel ?? roleOf.get(f.signerId) ?? 'CONTRATADO',
        })),
      });
    }
  }

  async setParticipants(user: AuthUser, id: string, parts: ParticipantDto[], ctx: Ctx) {
    const doc = await this.owned(user, id);
    this.assertDraft(doc);
    await this.validateSigners(user, parts.map((p) => p.signerId));
    await this.prisma.$transaction(async (tx) => {
      await this.writeParticipants(tx, id, parts);
      // remove campos de quem saiu do fluxo
      await tx.signaturefield.deleteMany({ where: { documentId: id, signerId: { notIn: parts.map((p) => p.signerId) } } });
    });
    return this.detail(user, id, ctx);
  }

  async setFields(user: AuthUser, id: string, fields: FieldDto[], ctx: Ctx) {
    const doc = await this.owned(user, id);
    this.assertDraft(doc);
    this.validateFields(fields, doc.totalPaginas);
    await this.validateSigners(user, fields.map((f) => f.signerId));
    await this.prisma.$transaction(async (tx) => {
      await this.writeFields(tx, id, fields);
      await this.event(tx, doc, AuditType.FIELDS_UPDATED, user, `${fields.length} campo(s) de assinatura posicionado(s).`, ctx);
    });
    return this.detail(user, id, ctx);
  }

  async send(user: AuthUser, id: string, dto: SendDto, ctx: Ctx) {
    const doc = await this.owned(user, id);
    if (doc.status !== DocumentStatus.DRAFT) {
      // Idempotência: nova tentativa não duplica as solicitações
      if (doc.status === DocumentStatus.CANCELLED) throw new ConflictException('Documento cancelado não pode ser enviado. Crie uma nova versão.');
      return { jaEnviado: true, mensagem: 'Este documento já havia sido enviado; nenhuma solicitação foi duplicada.', documento: await this.detail(user, id, ctx) };
    }
    if (dto.participantes) await this.validateSigners(user, dto.participantes.map((p) => p.signerId));
    if (dto.fields) {
      this.validateFields(dto.fields, doc.totalPaginas);
      await this.validateSigners(user, dto.fields.map((f) => f.signerId));
    }

    await this.prisma.$transaction(async (tx) => {
      if (dto.participantes) await this.writeParticipants(tx, id, dto.participantes);
      if (dto.fields) await this.writeFields(tx, id, dto.fields);
      const parts = await tx.documentsigner.findMany({ where: { documentId: id }, include: { signer: USER_PUB } });
      const fields = await tx.signaturefield.findMany({ where: { documentId: id } });
      if (!doc.titulo?.trim()) throw new BadRequestException('O título do documento é obrigatório.');
      if (parts.length < 1) throw new BadRequestException('O documento precisa ter pelo menos um signatário para ser enviado.');
      if (!parts.some((p) => p.required)) throw new BadRequestException('O documento precisa ter pelo menos um signatário obrigatório.');
      const partIds = new Set(parts.map((p) => p.signerId));
      const orfao = fields.find((f) => !partIds.has(f.signerId));
      if (orfao) throw new BadRequestException('Todo campo de assinatura deve estar associado a um participante do documento.');
      const semCampo = parts.find((p) => !fields.some((f) => f.signerId === p.signerId));
      if (semCampo) throw new BadRequestException(`O signatário ${semCampo.signer.nome} não possui nenhum campo de assinatura posicionado.`);
      for (const f of fields) {
        if (f.page > doc.totalPaginas || f.x < 0 || f.y < 0 || f.x + f.width > 1 + 1e-6 || f.y + f.height > 1 + 1e-6) {
          throw new BadRequestException('Existe campo de assinatura fora dos limites da página.');
        }
      }
      const now = new Date();
      // concorrência: só envia se ainda estiver em rascunho
      const upd = await tx.document.updateMany({ where: { id, status: DocumentStatus.DRAFT }, data: { status: DocumentStatus.PENDING, enviadoEm: now, mensagem: dto.mensagem ?? null } });
      if (upd.count === 0) throw new ConflictException('O documento já foi enviado.');
      await tx.documentsigner.updateMany({ where: { documentId: id }, data: { invitedAt: now } });
      await tx.documentversion.update({
        where: { documentId_versao: { documentId: id, versao: doc.versao } },
        data: { configuracao: { participantes: parts.map((p) => ({ signerId: p.signerId, roleLabel: p.roleLabel, required: p.required })), campos: fields, mensagem: dto.mensagem ?? null, enviadoEm: now.toISOString() } as unknown as Prisma.InputJsonValue },
      });
      await this.event(tx, doc, AuditType.SENT, user, `Documento enviado para ${parts.length} signatário(s): ${parts.map((p) => p.signer.nome).join(', ')}.`, ctx, {
        participantes: parts.map((p) => ({ id: p.signerId, nome: p.signer.nome, email: p.signer.email, papel: p.roleLabel })), totalCampos: fields.length,
      });
    });
    this.logger.log(`Documento ${id} enviado`);

    // Buscar participantes novamente para notificação (fora da transação)
    const partsForNotification = await this.prisma.documentsigner.findMany({
      where: { documentId: id },
      include: { signer: USER_PUB },
    });
    
    // Enviar notificações por e-mail (fora da transação para não bloquear)
    const signersForNotification = partsForNotification.map((p) => ({
      email: p.signer.email,
      nome: p.signer.nome,
      papel: p.roleLabel,
    }));
    this.notifications.notifyDocumentSent(signersForNotification, id, doc.titulo, user.nome, dto.mensagem).catch((err) => {
      this.logger.error(`Falha ao enviar notificações de envio para ${id}`, err.stack);
    });

    return { jaEnviado: false, mensagem: 'Documento enviado com sucesso.', documento: await this.detail(user, id, ctx) };
  }

  async newVersion(user: AuthUser, id: string, file: Express.Multer.File | undefined, titulo: string | undefined, ctx: Ctx) {
    const doc = await this.owned(user, id);
    if (doc.status === DocumentStatus.COMPLETED) throw new ConflictException('Documento concluído não pode receber nova versão. Crie um novo documento.');
    const info = await validatePdf(file);
    const buf = file!.buffer;
    const hash = sha256(buf);
    const versao = doc.versao + 1;
    await this.prisma.$transaction(async (tx) => {
      await tx.document.update({
        where: { id },
        data: {
          versao, hashSha256: hash, pdf: buf, tamanhoBytes: buf.length, nomeArquivo: file!.originalname || doc.nomeArquivo, totalPaginas: info.pages,
          status: DocumentStatus.DRAFT, enviadoEm: null, concluidoEm: null, pdfFinal: null, hashFinal: null, ...(titulo?.trim() ? { titulo: titulo.trim() } : {}),
        },
      });
      await tx.documentversion.create({ data: { documentId: id, versao, hashSha256: hash, nomeArquivo: file!.originalname || doc.nomeArquivo, tamanhoBytes: buf.length, pdf: buf } });
      await tx.documentsigner.updateMany({
        where: { documentId: id },
        data: { status: SignerStatus.PENDING, invitedAt: null, viewedAt: null, signedAt: null, rejectedAt: null, motivoRecusa: null, assinaturaImagemBase64: null, gestureMetadata: Prisma.JsonNull, ip: null, userAgent: null },
      });
      await tx.signaturefield.deleteMany({ where: { documentId: id, page: { gt: info.pages } } });
      await this.event(tx, { id, versao, hashSha256: hash }, AuditType.VERSION_CREATED, user, `Versão ${versao} criada (versão anterior ${doc.versao} preservada). Novo fluxo iniciado.`, ctx, { versaoAnterior: doc.versao, hashAnterior: doc.hashSha256 });
    });

    // Notificar participantes sobre nova versão
    const participants = await this.prisma.documentsigner.findMany({
      where: { documentId: id },
      include: { signer: USER_PUB },
    });
    for (const p of participants) {
      this.notifications.sendNewVersion(p.signer.email, {
        nomeDestinatario: p.signer.nome,
        tituloDocumento: doc.titulo,
        linkDocumento: id,
        nomeRemetente: user.nome,
      }).catch((err) => this.logger.error(`Falha ao notificar nova versão para ${p.signer.email}`, err.stack));
    }

    return this.detail(user, id, ctx);
  }

  async sign(user: AuthUser, id: string, dto: SignDto, ctx: Ctx) {
    const { doc, participant } = await this.access(user, id);
    if (!participant) throw new ForbiddenException('Apenas signatários convidados podem assinar este documento.');
    if (doc.status !== DocumentStatus.PENDING && doc.status !== DocumentStatus.PARTIALLY_SIGNED) {
      throw new ConflictException('Este documento não está disponível para assinatura.');
    }
    if (participant.status === SignerStatus.SIGNED) throw new ConflictException('Você já assinou este documento.');
    if (participant.status === SignerStatus.REJECTED) throw new ConflictException('Você recusou este documento e não pode mais assiná-lo.');
    const png = cleanBase64(dto.assinaturaImagemBase64);
    const bytes = Buffer.from(png, 'base64');
    if (bytes.length < 8 || bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a') {
      throw new BadRequestException('A imagem da assinatura deve ser um PNG válido em base64.');
    }
    if (bytes.length > 2 * 1024 * 1024) throw new BadRequestException('A imagem da assinatura deve ter no máximo 2 MB.');
    const now = new Date();
    const meta = { ...(dto.gestureMetadata ?? {}), hashImagemAssinatura: sha256(bytes) };

    await this.prisma.$transaction(async (tx) => {
      const upd = await tx.documentsigner.updateMany({
        where: { id: participant.id, status: { in: [SignerStatus.PENDING, SignerStatus.VIEWED] } },
        data: { status: SignerStatus.SIGNED, signedAt: now, viewedAt: participant.viewedAt ?? now, assinaturaImagemBase64: png, gestureMetadata: meta as Prisma.InputJsonValue, ip: ctx.ip, userAgent: ctx.userAgent },
      });
      if (upd.count === 0) throw new ConflictException('Você já assinou este documento.');
      await this.event(tx, doc, AuditType.SIGNED, user, `${user.nome} assinou o documento.`, ctx, { gesto: meta, userAgent: ctx.userAgent });
      const parts = await tx.documentsigner.findMany({ where: { documentId: id } });
      const pendentesObrig = parts.filter((p) => p.required && p.status !== SignerStatus.SIGNED);
      if (pendentesObrig.length === 0) {
        await tx.document.update({ where: { id }, data: { status: DocumentStatus.COMPLETED, concluidoEm: now } });
      } else {
        await tx.document.update({ where: { id }, data: { status: DocumentStatus.PARTIALLY_SIGNED } });
      }
    }, { timeout: 15000 });

    const after = await this.prisma.document.findUniqueOrThrow({ where: { id }, select: { status: true } });
    
    // Notificar outros participantes sobre a assinatura
    const otherParticipants = await this.prisma.documentsigner.findMany({
      where: { documentId: id, signerId: { not: user.id } },
      include: { signer: USER_PUB },
    });
    this.notifications.notifySignerSigned(
      otherParticipants.map((p) => p.signer.email).join(','), // We'll handle this differently
      '', // Will be handled in loop
      id, doc.titulo, user.nome
    ).catch((err) => {
      this.logger.error(`Falha ao notificar assinatura para ${id}`, err.stack);
    });
    
    // Send individual notifications
    for (const p of otherParticipants) {
      this.notifications.notifySignerSigned(p.signer.email, p.signer.nome, id, doc.titulo, user.nome).catch((err) => {
        this.logger.error(`Falha ao notificar ${p.signer.email} sobre assinatura`, err.stack);
      });
    }
    
    if (after.status === DocumentStatus.COMPLETED) await this.finalize(id, user, ctx);
    return { mensagem: after.status === DocumentStatus.COMPLETED ? 'Assinatura registrada. Documento concluído!' : 'Assinatura registrada com sucesso.', documento: await this.detail(user, id, ctx) };
  }

  private async finalize(id: string, user: AuthUser, ctx: Ctx) {
    const d = await this.prisma.document.findUniqueOrThrow({
      where: { id },
      include: { fields: true, signers: { include: { signer: { select: { nome: true, email: true } } } } },
    });
    const bySigner = new Map(d.signers.map((s) => [s.signerId, s]));
    const stamps = d.fields
      .map((f) => {
        const s = bySigner.get(f.signerId);
        if (!s?.assinaturaImagemBase64 || s.status !== SignerStatus.SIGNED) return null;
        return { page: f.page, x: f.x, y: f.y, width: f.width, height: f.height, png: s.assinaturaImagemBase64, label: `${s.signer.nome} - ${s.roleLabel} - ${s.signedAt?.toISOString()}` };
      })
      .filter((x): x is NonNullable<typeof x> => !!x);
    let finalBuf: Buffer;
    try {
      finalBuf = await stampPdf(Buffer.from(d.pdf), stamps, `EasySign - documento ${d.id} v${d.versao} - hash original SHA-256 ${d.hashSha256}`);
    } catch (e) {
      this.logger.error(`Falha ao gerar PDF final de ${id}`, (e as Error).stack);
      finalBuf = Buffer.from(d.pdf);
    }
    const hashFinal = sha256(finalBuf);
    await this.prisma.$transaction(async (tx) => {
      await tx.document.update({ where: { id }, data: { pdfFinal: finalBuf, hashFinal } });
      await this.event(tx, d, AuditType.COMPLETED, null, 'Todos os signatários obrigatórios assinaram. Documento concluído.', ctx, { hashOriginal: d.hashSha256, hashFinal, ultimoSignatario: user.nome }, hashFinal);
    });

    // Notificar todos os participantes sobre a conclusão
    const recipients = d.signers.map((s) => ({ email: s.signer.email, nome: s.signer.nome }));
    this.notifications.notifyDocumentCompleted(recipients, id, d.titulo).catch((err) => {
      this.logger.error(`Falha ao notificar conclusão para ${id}`, err.stack);
    });
  }

  async reject(user: AuthUser, id: string, motivo: string, ctx: Ctx) {
    const { doc, participant } = await this.access(user, id);
    if (!participant) throw new ForbiddenException('Apenas signatários convidados podem recusar este documento.');
    if (doc.status !== DocumentStatus.PENDING && doc.status !== DocumentStatus.PARTIALLY_SIGNED) throw new ConflictException('Este documento não está disponível para recusa.');
    if (participant.status === SignerStatus.SIGNED || participant.status === SignerStatus.REJECTED) throw new ConflictException('Você já respondeu a este documento.');
    await this.prisma.$transaction(async (tx) => {
      await tx.documentsigner.update({ where: { id: participant.id }, data: { status: SignerStatus.REJECTED, rejectedAt: new Date(), motivoRecusa: motivo, ip: ctx.ip, userAgent: ctx.userAgent } });
      if (participant.required) await tx.document.update({ where: { id }, data: { status: DocumentStatus.CANCELLED } });
      await this.event(tx, doc, AuditType.REJECTED, user, `${user.nome} recusou o documento. Motivo: ${motivo}`, ctx, { motivo, documentoCancelado: participant.required });
    });

    // Notificar sobre recusa
    if (participant.required) {
      // Documento cancelado - notificar todos
      const allParticipants = await this.prisma.documentsigner.findMany({
        where: { documentId: id },
        include: { signer: USER_PUB },
      });
      for (const p of allParticipants) {
        if (p.signerId !== user.id) {
          this.notifications.notifyDocumentCancelled(
            [{ email: p.signer.email, nome: p.signer.nome }],
            id, doc.titulo, motivo
          ).catch((err) => this.logger.error(`Falha ao notificar cancelamento para ${p.signer.email}`, err.stack));
        }
      }
      // Notificar admin
      const owner = await this.prisma.user.findUnique({ where: { id: doc.ownerId }, select: USER_PUB.select });
      if (owner) {
        this.notifications.notifyDocumentCancelled(
          [{ email: owner.email, nome: owner.nome }],
          id, doc.titulo, motivo
        ).catch((err) => this.logger.error(`Falha ao notificar admin sobre cancelamento`, err.stack));
      }
    } else {
      // Apenas notificar admin que um signatário opcional recusou
      const owner = await this.prisma.user.findUnique({ where: { id: doc.ownerId }, select: USER_PUB.select });
      if (owner) {
        this.notifications.notifyDocumentRejected(
          owner.email, owner.nome, id, doc.titulo, user.nome, motivo
        ).catch((err) => this.logger.error(`Falha ao notificar admin sobre recusa`, err.stack));
      }
    }

    return { mensagem: 'Recusa registrada.', documento: await this.detail(user, id, ctx) };
  }

  async audit(user: AuthUser, id: string) {
    const { doc } = await this.access(user, id);
    const events = await this.prisma.auditevent.findMany({ where: { documentId: id }, orderBy: { timestamp: 'asc' } });
    return { documentId: id, titulo: doc.titulo, versaoAtual: doc.versao, hashAtual: doc.hashSha256, hashFinal: doc.hashFinal, status: doc.status, eventos: events };
  }

  async verify(user: AuthUser, id: string, hash: string) {
    const { doc } = await this.access(user, id);
    const versions = await this.prisma.documentversion.findMany({ where: { documentId: id }, select: { versao: true, hashSha256: true } });
    const h = hash.toLowerCase().trim();
    const v = versions.find((x) => x.hashSha256 === h);
    if (doc.hashFinal === h) return { valido: true, tipo: 'DOCUMENTO_FINAL_ASSINADO', versao: doc.versao };
    if (v) return { valido: true, tipo: 'ORIGINAL', versao: v.versao };
    return { valido: false, mensagem: 'O hash informado não corresponde a nenhuma versão deste documento.' };
  }
}
